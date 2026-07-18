package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.domain.model.SaveState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class DoctorVisitServiceTest {

    /** Scripted rolls: pops the next queued value whatever bound is asked for. */
    private static IntUnaryOperator rolls(int... values) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int v : values) {
            queue.add(v);
        }
        return n -> queue.pop();
    }

    @Test
    void noTriggerConditionMeansNoVisit() {
        SaveState save = TestSaves.newSave();   // cash 100, happiness 50, time 3600
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls()).resolve(save, false, false, false);

        assertFalse(outcome.triggered());
        assertEquals(100, save.cash());
        assertEquals(50, save.happiness());
        assertEquals(3600, save.timeMinutes());
    }

    @Test
    void starvationRollCanMissAtTwentyFivePercent() {
        SaveState save = TestSaves.newSave();
        // 1-in-4 roll, value 2 = miss.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(2)).resolve(save, true, false, false);

        assertFalse(outcome.triggered());
        assertEquals(100, save.cash());
    }

    @Test
    void starvationTriggerCostsTenHoursFourHappinessAndTieredCash() {
        SaveState save = TestSaves.newSave();   // cash 100 -> $50-499 tier
        save.setTimeMinutes(3600);
        // 1-in-4 roll, value 1 = hit; cost roll on a 21-wide range (30..50), value 1 -> 30.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 1)).resolve(save, true, false, false);

        assertTrue(outcome.triggered());
        assertEquals(600, outcome.minutesLost());
        assertEquals(4, outcome.happinessLost());
        assertEquals(30, outcome.cashLost());
        assertEquals(3000, save.timeMinutes());
        assertEquals(46, save.happiness());      // 50 - 4
        assertEquals(70, save.cash());            // 100 - 30
    }

    @Test
    void costTierAtOrAboveFiveHundredRangesThirtyToTwoHundred() {
        SaveState save = TestSaves.newSave();
        save.setCash(500);
        // hit; cost roll on a 171-wide range (30..200), value 171 -> 200.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 171)).resolve(save, true, false, false);

        assertEquals(200, outcome.cashLost());
        assertEquals(300, save.cash());
    }

    @Test
    void costTierBelowFiftyRangesThirtyToCashOnHand() {
        SaveState save = TestSaves.newSave();
        save.setCash(40);   // 31..49 tier: range is 30..40, width 11
        // hit; cost roll value 11 -> 30 + 11 - 1 = 40 (all cash).
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 11)).resolve(save, true, false, false);

        assertEquals(40, outcome.cashLost());
        assertEquals(0, save.cash());
    }

    @Test
    void costTierAtOrBelowThirtyChargesAllCashWithNoRoll() {
        SaveState save = TestSaves.newSave();
        save.setCash(25);
        // hit; no cost roll consumed — the queue would throw if one were requested.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1)).resolve(save, true, false, false);

        assertEquals(25, outcome.cashLost());
        assertEquals(0, save.cash());
    }

    @Test
    void zeroCashBypassesTheEventEntirely() {
        SaveState save = TestSaves.newSave();
        save.setCash(0);
        // Even a guaranteed-hit roll queue must never be consumed.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls()).resolve(save, true, false, false);

        assertFalse(outcome.triggered());
        assertEquals(0, save.cash());
        assertEquals(50, save.happiness());
    }

    @Test
    void spoilageRollCanMissAtFiftyPercent() {
        SaveState save = TestSaves.newSave();
        // 1-in-2 roll, value 2 = miss.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(2)).resolve(save, false, true, false);

        assertFalse(outcome.triggered());
    }

    @Test
    void spoilageTriggerCostsTheSameAsStarvation() {
        SaveState save = TestSaves.newSave();
        // 1-in-2 roll, value 1 = hit; cost roll value 1 -> 30.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 1)).resolve(save, false, true, false);

        assertTrue(outcome.triggered());
        assertEquals(30, outcome.cashLost());
    }

    @Test
    void starvationAndSpoilageBothRollButOnlyOneVisitResolves() {
        SaveState save = TestSaves.newSave();
        // Starvation roll (1-in-4) value 4 = miss; spoilage roll (1-in-2) value 1 = hit;
        // cost roll value 1 -> 30. Exactly one visit's cost is charged.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(4, 1, 1)).resolve(save, true, true, false);

        assertTrue(outcome.triggered());
        assertEquals(30, outcome.cashLost());
        assertEquals(70, save.cash());
    }

    @Test
    void relaxationFloorRollCanMissAtTwentyPercent() {
        SaveState save = TestSaves.newSave();
        // 1-in-5 roll, value 2 = miss.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(2)).resolve(save, false, false, true);

        assertFalse(outcome.triggered());
    }

    @Test
    void relaxationFloorTriggerCostsTheSameAsTheOtherTwo() {
        SaveState save = TestSaves.newSave();
        // 1-in-5 roll, value 1 = hit; cost roll value 1 -> 30.
        DoctorVisitOutcome outcome = new DoctorVisitService(rolls(1, 1)).resolve(save, false, false, true);

        assertTrue(outcome.triggered());
        assertEquals(30, outcome.cashLost());
    }
}
