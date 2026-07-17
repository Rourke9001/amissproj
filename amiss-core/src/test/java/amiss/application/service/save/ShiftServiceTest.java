package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.config.ActionCosts;
import amiss.application.port.JobCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private SaveRepository saves;
    @Mock
    private JobCatalog jobs;
    @Mock
    private SaveDegrees degrees;

    private ShiftService service() {
        return new ShiftService(saves, jobs, degrees, ActionCosts.defaults());
    }

    /** Fresh save employed as the Monolith Assistant Manager (wage 7, req dep 30). */
    private SaveState employedSave() {
        SaveState save = TestSaves.newSave();
        save.setJobId(TestSaves.ASSISTANT.id());
        save.setWage(TestSaves.ASSISTANT.wage());
        save.setDependability(30);
        when(jobs.byId(TestSaves.ASSISTANT.id())).thenReturn(Optional.of(TestSaves.ASSISTANT));
        return save;
    }

    @Test
    void unemployedCannotWork() {
        ShiftOutcome outcome = service().work(TestSaves.newSave());
        assertEquals(ShiftOutcome.Status.NO_JOB, outcome.status());
    }

    @Test
    void underdressedIsRefusedWithoutCharge() {
        SaveState save = TestSaves.newSave();
        save.setJobId(TestSaves.BROKER.id());
        save.setDependability(70);
        when(jobs.byId(TestSaves.BROKER.id())).thenReturn(Optional.of(TestSaves.BROKER));

        ShiftOutcome outcome = service().work(save);   // clothing 1 < Business (3)

        assertEquals(ShiftOutcome.Status.UNDERDRESSED, outcome.status());
        assertEquals(3600, save.timeMinutes());
    }

    @Test
    void aFullSessionPaysWageTimesEightAndGrowsBothStats() {
        SaveState save = employedSave();
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());

        ShiftOutcome outcome = service().work(save);

        assertEquals(ShiftOutcome.Status.OK, outcome.status());
        assertEquals(56, outcome.pay());              // 7 * 8
        assertEquals(56, outcome.netPaid());
        assertEquals(156, save.cash());
        assertEquals(360, outcome.minutesCharged());
        assertEquals(3240, save.timeMinutes());
        assertEquals(11, save.experience());          // +1 under cap (30)
        assertEquals(31, save.dependability());       // +1 under cap (50)
        assertFalse(outcome.warning());
        verify(saves).update(save);
    }

    @Test
    void aShortClockProRatesThePayAndStillSucceeds() {
        SaveState save = employedSave();
        save.setTimeMinutes(180);                      // 3h of a 6h shift
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());

        ShiftOutcome outcome = service().work(save);

        assertEquals(ShiftOutcome.Status.OK, outcome.status());
        assertEquals(28, outcome.pay());               // 7 * 8 * 180/360
        assertEquals(0, save.timeMinutes());
        assertEquals(11, save.experience());           // a paying session still grows stats
    }

    @Test
    void statsStopAtTheirCaps() {
        SaveState save = employedSave();
        save.setExperience(30);                        // cap = 10 + 20 + 0
        save.setDependability(50);                     // cap = 20 + 30 + 0
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());

        service().work(save);

        assertEquals(30, save.experience());
        assertEquals(50, save.dependability());
    }

    @Test
    void degreesRaiseTheCaps() {
        SaveState save = employedSave();
        save.setExperience(30);
        save.setDependability(50);
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of(1, 2));   // +10 on both caps

        service().work(save);

        assertEquals(31, save.experience());
        assertEquals(51, save.dependability());
    }

    @Test
    void firedWhenDependabilityFallsMoreThanFiveBelowRequirement() {
        SaveState save = employedSave();
        save.setDependability(24);                     // req 30: 24 < 30 - 5

        ShiftOutcome outcome = service().work(save);

        assertEquals(ShiftOutcome.Status.FIRED, outcome.status());
        assertNull(save.jobId());
        assertEquals(47, save.happiness());            // -3
        assertEquals(3600, save.timeMinutes());        // no time charged
        verify(saves).update(save);
    }

    @Test
    void theWarningBandIsThreeToFiveBelow() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());

        SaveState atFiveBelow = employedSave();
        atFiveBelow.setDependability(25);
        assertTrue(service().work(atFiveBelow).warning());   // 25 = 30-5: warned, still works

        SaveState atThreeBelow = employedSave();
        atThreeBelow.setDependability(27);
        assertTrue(service().work(atThreeBelow).warning());

        SaveState atTwoBelow = employedSave();
        atTwoBelow.setDependability(28);
        assertFalse(service().work(atTwoBelow).warning());
    }

    @Test
    void debtGarnishesHalfThePayPlusTwoInterest() {
        SaveState save = employedSave();
        save.setDebt(80);
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());

        ShiftOutcome outcome = service().work(save);

        assertEquals(56, outcome.pay());
        assertEquals(28, outcome.garnished());
        assertEquals(26, outcome.netPaid());           // 56 - 28 - 2
        assertEquals(52, save.debt());
        assertEquals(126, save.cash());
    }

    @Test
    void garnishNeverExceedsTheRemainingDebt() {
        SaveState save = employedSave();
        save.setDebt(10);
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());

        ShiftOutcome outcome = service().work(save);

        assertEquals(10, outcome.garnished());
        assertEquals(0, save.debt());
        assertEquals(44, outcome.netPaid());           // 56 - 10 - 2
    }

    @Test
    void weekOverRefusesTheShift() {
        SaveState save = employedSave();
        save.setTimeMinutes(0);

        ShiftOutcome outcome = service().work(save);

        assertEquals(ShiftOutcome.Status.WEEK_OVER, outcome.status());
    }

    // ===== wage snapshotting =====

    @Test
    void workPaysFromTheWageSnapshotNotTheCatalog() {
        SaveState save = TestSaves.newSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(10);                          // snapshot differs from base 5
        when(jobs.byId(TestSaves.COOK.id())).thenReturn(Optional.of(TestSaves.COOK));

        ShiftOutcome outcome = service().work(save);

        assertEquals(80, outcome.pay());           // 10 * 8 full session, not 40
    }

    @Test
    void firingForLowDependabilityClearsTheWageSnapshot() {
        SaveState save = TestSaves.newSave();
        save.setJobId(TestSaves.ASSISTANT.id());
        save.setWage(7);
        save.setDependability(0);                  // 30-required, > 5 below -> fired
        when(jobs.byId(TestSaves.ASSISTANT.id())).thenReturn(Optional.of(TestSaves.ASSISTANT));

        assertEquals(ShiftOutcome.Status.FIRED, service().work(save).status());
        assertNull(save.wage());
    }
}
