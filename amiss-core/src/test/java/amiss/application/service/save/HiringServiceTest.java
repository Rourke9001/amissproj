package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.config.ActionCosts;
import amiss.application.port.JobCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.application.port.Turndowns;
import amiss.domain.model.SaveState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HiringServiceTest {

    @Mock
    private SaveRepository saves;
    @Mock
    private JobCatalog jobs;
    @Mock
    private SaveDegrees degrees;
    @Mock
    private Turndowns turndowns;

    private HiringService service(IntSupplier roll) {
        return new HiringService(saves, jobs, degrees, turndowns, ActionCosts.defaults(), roll);
    }

    private HiringService serviceAlwaysLucky() {
        return service(() -> 1);
    }

    // ===== plumbing outcomes =====

    @Test
    void unknownJobChargesNothing() {
        when(jobs.byId(999)).thenReturn(Optional.empty());
        SaveState save = TestSaves.newSave();

        HireOutcome outcome = serviceAlwaysLucky().apply(save, 999);

        assertEquals(HireOutcome.Status.UNKNOWN_JOB, outcome.status());
        assertEquals(4320, save.timeMinutes());
    }

    @Test
    void weekOverRefusesBeforeCharging() {
        when(jobs.byId(4)).thenReturn(Optional.of(TestSaves.COOK));
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(0);

        HireOutcome outcome = serviceAlwaysLucky().apply(save, 4);

        assertEquals(HireOutcome.Status.WEEK_OVER, outcome.status());
    }

    // ===== the Cook bypass =====

    @Test
    void cookIsAlwaysHiredEvenWithAnImpossibleRoll() {
        when(jobs.byId(4)).thenReturn(Optional.of(TestSaves.COOK));
        SaveState save = TestSaves.newSave();
        save.setDependability(0);
        save.setExperience(0);

        HireOutcome outcome = service(() -> 100).apply(save, 4);

        assertEquals(HireOutcome.Status.HIRED, outcome.status());
        assertEquals(5, outcome.wage());
        assertEquals(240, outcome.minutesCharged());
        assertEquals(4080, save.timeMinutes());
    }

    // ===== requirement checks, in wiki order, all reported =====

    @Test
    void reportsEveryLackingNamedStat() {
        when(jobs.byId(32)).thenReturn(Optional.of(TestSaves.BROKER));
        when(jobs.requiredDegrees(32)).thenReturn(Set.of(3, 4));
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of(3));
        SaveState save = TestSaves.newSave();
        save.setRound(9); // past the weeks-1-4 suppression

        HireOutcome outcome = serviceAlwaysLucky().apply(save, 32);

        assertEquals(HireOutcome.Status.REJECTED, outcome.status());
        assertEquals(List.of(HireOutcome.Reason.NOT_ENOUGH_EDUCATION,
                HireOutcome.Reason.NOT_ENOUGH_EXPERIENCE,
                HireOutcome.Reason.POOR_WORK_HISTORY), outcome.reasons());
    }

    @Test
    void rejectionChargesTheInterviewAndOneHappiness() {
        when(jobs.byId(32)).thenReturn(Optional.of(TestSaves.BROKER));
        when(jobs.requiredDegrees(32)).thenReturn(Set.of(3, 4));
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = TestSaves.newSave();

        HireOutcome outcome = serviceAlwaysLucky().apply(save, 32);

        assertEquals(240, outcome.minutesCharged());
        assertEquals(4080, save.timeMinutes());
        assertEquals(49, save.happiness());
        verify(saves).update(save);
    }

    @Test
    void dependabilityShortfallMasksAsNoOpeningsInWeeksOneToFour() {
        when(jobs.byId(6)).thenReturn(Optional.of(TestSaves.ASSISTANT));
        when(jobs.requiredDegrees(6)).thenReturn(Set.of());
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = TestSaves.newSave();  // round 1, dep 20 < 30, exp 10 < 20
        save.setExperience(20);

        HireOutcome outcome = serviceAlwaysLucky().apply(save, 6);

        assertEquals(List.of(HireOutcome.Reason.NO_OPENINGS), outcome.reasons());
    }

    @Test
    void dependabilityShortfallIsNamedFromWeekFive() {
        when(jobs.byId(6)).thenReturn(Optional.of(TestSaves.ASSISTANT));
        when(jobs.requiredDegrees(6)).thenReturn(Set.of());
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = TestSaves.newSave();
        save.setExperience(20);
        save.setRound(5);

        HireOutcome outcome = serviceAlwaysLucky().apply(save, 6);

        assertEquals(List.of(HireOutcome.Reason.POOR_WORK_HISTORY), outcome.reasons());
    }

    @Test
    void listedDependabilityTenTrulyRequiresZero() {
        when(jobs.byId(1)).thenReturn(Optional.of(TestSaves.CLERK));
        when(jobs.requiredDegrees(1)).thenReturn(Set.of());
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = TestSaves.newSave();
        save.setDependability(0);   // below the listed 10, but effective req is 0

        HireOutcome outcome = serviceAlwaysLucky().apply(save, 1);

        assertEquals(HireOutcome.Status.HIRED, outcome.status());
    }

    // ===== luck =====

    @Test
    void rollEqualToLuckHiresAndOneAboveRefuses() {
        when(jobs.byId(1)).thenReturn(Optional.of(TestSaves.CLERK));
        when(jobs.requiredDegrees(1)).thenReturn(Set.of());
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        when(turndowns.isTurnedDown(anyLong(), anyInt(), anyInt())).thenReturn(false);

        // Fresh save: dep 20, exp 10, 0 degrees => luck 43.
        HireOutcome atLuck = service(() -> 43).apply(TestSaves.newSave(), 1);
        assertEquals(HireOutcome.Status.HIRED, atLuck.status());

        HireOutcome aboveLuck = service(() -> 44).apply(TestSaves.newSave(), 1);
        assertEquals(HireOutcome.Status.REJECTED, aboveLuck.status());
        assertEquals(List.of(HireOutcome.Reason.NO_OPENINGS), aboveLuck.reasons());
        verify(turndowns).record(TestSaves.SAVE_ID, 1, 1);
    }

    @Test
    void aTurnedDownJobStaysNoOpeningsWithoutReRolling() {
        when(jobs.byId(1)).thenReturn(Optional.of(TestSaves.CLERK));
        when(jobs.requiredDegrees(1)).thenReturn(Set.of());
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        when(turndowns.isTurnedDown(TestSaves.SAVE_ID, 1, 1)).thenReturn(true);
        SaveState save = TestSaves.newSave();

        HireOutcome outcome = serviceAlwaysLucky().apply(save, 1);

        assertEquals(List.of(HireOutcome.Reason.NO_OPENINGS), outcome.reasons());
    }

    // ===== hire side-effects =====

    @Test
    void hiringGrantsTheSwitchBonusFloorsDependabilityAndCheersUp() {
        when(jobs.byId(1)).thenReturn(Optional.of(TestSaves.CLERK));
        when(jobs.requiredDegrees(1)).thenReturn(Set.of());
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        when(turndowns.isTurnedDown(anyLong(), anyInt(), anyInt())).thenReturn(false);
        SaveState save = TestSaves.newSave();
        save.setDependability(4);   // eligible (effective req 0) but under the anti-frustration floor

        HireOutcome outcome = serviceAlwaysLucky().apply(save, 1);

        assertEquals(HireOutcome.Status.HIRED, outcome.status());
        assertEquals(1, save.jobId());
        assertEquals(12, save.experience());       // +2, cap-exempt
        assertEquals(10, save.dependability());    // floored up to 10
        assertEquals(53, save.happiness());        // +3
        verify(saves).update(save);
        assertTrue(outcome.reasons().isEmpty());
        assertFalse(save.weekOver());
        assertNull(save.currentCourseId());
    }
}
