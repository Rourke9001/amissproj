package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeekRolloverServiceTest {

    @Mock
    private SaveRepository saves;
    @Mock
    private SaveDegrees degrees;

    /** Scripted rolls: pops the next queued value whatever bound is asked for. */
    private static IntUnaryOperator rolls(int... values) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int v : values) {
            queue.add(v);
        }
        return n -> queue.pop();
    }

    /**
     * Neutral economy: index step 0 (roll 2 on the 1..3 die), noise 0 (roll 6 on
     * 1..11); on rounds >= 8 the event die (1..31) also rolls 2, which never fires.
     */
    private static IntUnaryOperator neutralRolls() {
        return n -> n == 3 ? 2 : n == 31 ? 2 : 6;
    }

    private WeekRolloverService service() {
        return service(neutralRolls());
    }

    private WeekRolloverService service(IntUnaryOperator rolls) {
        return new WeekRolloverService(saves, new GoalService(degrees), ActionCosts.defaults(),
                new EconomyService(rolls));
    }

    /** A save with the clock run out, ready to roll over. */
    private SaveState weekOverSave() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(0);
        return save;
    }

    @Test
    void refusesWhileTimeRemains() {
        SaveState save = TestSaves.newSave();

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertFalse(result.rolled());
        verifyNoInteractions(saves);
    }

    @Test
    void anUnfedWeekRollsToTheBaseBudgetAndDecaysDependability() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertTrue(result.rolled());
        assertEquals(2, result.newRound());
        assertFalse(result.fed());
        assertEquals(3600, save.timeMinutes());
        assertEquals(17, save.dependability());   // 20 - 3
        verify(saves).update(save);
    }

    @Test
    void aFedWeekConsumesFoodAndGetsTheLongBudget() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setEat(2);

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertTrue(result.fed());
        assertEquals(4320, save.timeMinutes());
        assertEquals(1, save.eat());
    }

    @Test
    void dependabilityNeverDecaysBelowZero() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setDependability(2);

        service().endWeek(save);

        assertEquals(0, save.dependability());
    }

    @Test
    void aClosedRentRoundWithUnpaidRentChargesTheLateDebt() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setRound(4);
        save.setRent(1);

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertTrue(result.debtCharged());
        assertEquals(80, save.debt());
        assertFalse(result.rentDue());   // round 5 isn't a rent round
    }

    @Test
    void everyFourthRoundFlagsRentDue() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();
        save.setRound(3);
        save.setRent(0);

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertTrue(result.rentDue());
        assertEquals(1, save.rent());
    }

    @Test
    void winningRequiresAllFourGoalsAtRollover() {
        // Goals are 50/50/50/50. Build a state meeting all four:
        // wealth (cash+bank)/100 >= 50, happiness >= 50, education 1+9*6=55 >= 50,
        // career 1.25*40=50 >= 50 while employed.
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of(1, 2, 3, 4, 5, 6));
        SaveState save = weekOverSave();
        save.setCash(3000);
        save.setBank(2000);
        save.setJobId(TestSaves.CLERK.id());
        save.setDependability(43);   // decays to 40 before the check: career hits exactly 50

        WeekRolloverService.RolloverResult result = service().endWeek(save);

        assertTrue(result.won());
        assertTrue(save.won());
    }

    @Test
    void oneMissingGoalIsNotAWin() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of(1, 2, 3, 4, 5, 6));
        SaveState save = weekOverSave();
        save.setCash(3000);
        save.setBank(2000);
        save.setJobId(TestSaves.CLERK.id());
        save.setDependability(42);   // decays to 39: career 48 < 50

        assertFalse(service().endWeek(save).won());
    }

    @Test
    void unemployedCareerIsZeroSoNoWin() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of(1, 2, 3, 4, 5, 6));
        SaveState save = weekOverSave();
        save.setCash(9000);
        save.setDependability(90);
        save.addHappiness(50);

        assertFalse(service().endWeek(save).won());
    }

    @Test
    void wonIsStickyEvenIfGoalsLaterSlip() {
        // No degrees stub: a won save short-circuits the goal check entirely.
        SaveState save = weekOverSave();
        save.setWon(true);
        save.setCash(0);

        assertTrue(service().endWeek(save).won());
        assertTrue(save.won());
    }

    @Test
    void rolloverDriftsTheEconomy() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        SaveState save = weekOverSave();

        // Index roll 3 -> +1; noise roll 6 -> 0: reading 0 -> 10.
        service(rolls(3, 6)).endWeek(save);

        assertEquals(1, save.economyIndex());
        assertEquals(10, save.economyReading());
    }

    @Test
    void crashEffectsLandBeforeTheWinCheck() {
        // The winningRequiresAllFourGoals fixture, but a MAJOR crash wipes the bank
        // first: wealth (3000+0)/100 = 30 < 50 -> no win.
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of(1, 2, 3, 4, 5, 6));
        SaveState save = weekOverSave();
        save.setRound(8);
        save.setEconomyReading((short) 85);
        save.setCash(3000);
        save.setBank(2000);
        save.setJobId(TestSaves.CLERK.id());
        save.setWage(5);
        save.setDependability(43);

        // Rolls: index 2 (step 0), noise 6 (0), crash 1, severity 3 -> MAJOR.
        WeekRolloverService.RolloverResult result = service(rolls(2, 6, 1, 3)).endWeek(save);

        assertEquals(EconomyEvent.Type.CRASH, result.economy().type());
        assertFalse(result.won());                 // bank wiped + fired before the check
        assertEquals(0, save.bank());
    }

    @Test
    void quietWeeksReportNoEconomyEvent() {
        when(degrees.earned(TestSaves.SAVE_ID)).thenReturn(Set.of());
        assertEquals(EconomyEvent.Type.NONE,
                service().endWeek(weekOverSave()).economy().type());
    }
}
