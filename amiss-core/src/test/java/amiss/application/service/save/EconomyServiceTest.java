package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.domain.model.SaveState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class EconomyServiceTest {

    /** Scripted rolls: pops the next queued value whatever bound is asked for. */
    private static IntUnaryOperator rolls(int... values) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int v : values) {
            queue.add(v);
        }
        return n -> queue.pop();
    }

    private static SaveState saveWithReading(int reading) {
        SaveState save = TestSaves.newSave();
        save.setEconomyReading((short) reading);
        return save;
    }

    @Test
    void readingZeroMeansBasePrice() {
        assertEquals(32, new EconomyService(rolls()).price(32, saveWithReading(0)));
    }

    @Test
    void readingFloorHalvesPrices() {
        // -30/60 = -50%: the wiki's cheap extreme.
        assertEquals(16, new EconomyService(rolls()).price(32, saveWithReading(-30)));
    }

    @Test
    void readingCeilingMeans250Percent() {
        // +90/60 = +150%: 20 -> 50.
        assertEquals(50, new EconomyService(rolls()).price(20, saveWithReading(90)));
    }

    @Test
    void negativeReadingsRoundDownDeterministically() {
        // floorDiv(25 * -29, 60) = floorDiv(-725, 60) = -13 -> 12 (not -12 -> 13).
        assertEquals(12, new EconomyService(rolls()).price(25, saveWithReading(-29)));
    }

    @Test
    void driftMovesIndexThenReadingWithMomentumAndNoise() {
        // Roll 3 -> index +1 (0 -> 1); roll 6 -> noise 0. Reading 0 + 10*1 + 0 = 10.
        SaveState save = saveWithReading(0);
        new EconomyService(rolls(3, 6)).driftWeekly(save);
        assertEquals(1, save.economyIndex());
        assertEquals(10, save.economyReading());
    }

    @Test
    void driftClampsIndexAndReadingAtTheirBounds() {
        SaveState save = saveWithReading(90);
        save.setEconomyIndex((byte) 3);
        // Roll 3 -> +1 clamps at +3; noise roll 11 -> +5; reading 90 + 30 + 5 clamps at 90.
        new EconomyService(rolls(3, 11)).driftWeekly(save);
        assertEquals(3, save.economyIndex());
        assertEquals(90, save.economyReading());
    }

    @Test
    void driftClampsAtTheFloorToo() {
        SaveState save = saveWithReading(-30);
        save.setEconomyIndex((byte) -3);
        // Roll 1 -> -1 clamps at -3; noise roll 1 -> -5; reading floor holds.
        new EconomyService(rolls(1, 1)).driftWeekly(save);
        assertEquals(-3, save.economyIndex());
        assertEquals(-30, save.economyReading());
    }

    private static SaveState eventEligibleSave() {
        SaveState save = TestSaves.newSave();
        save.setRound(8);
        save.setEconomyReading((short) 85);
        return save;
    }

    @Test
    void noEventsBeforeWeekEight() {
        SaveState save = eventEligibleSave();
        save.setRound(7);
        // Would-be triggering rolls queued — they must never be consumed.
        assertEquals(EconomyEvent.Type.NONE,
                new EconomyService(rolls()).rollEvent(save).type());
    }

    @Test
    void crashNeedsReadingAtLeast80() {
        SaveState save = eventEligibleSave();
        save.setEconomyReading((short) 79);
        // First roll = boom roll (crash ineligible): 2 -> no boom.
        assertEquals(EconomyEvent.Type.NONE,
                new EconomyService(rolls(2)).rollEvent(save).type());
    }

    @Test
    void minorCrashOnlyDropsPricesAndHappiness() {
        SaveState save = eventEligibleSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(5);
        save.setBank(200);

        // Crash roll 1 -> crash; severity roll 1 -> MINOR.
        EconomyEvent event = new EconomyService(rolls(1, 1)).rollEvent(save);

        assertEquals(EconomyEvent.Type.CRASH, event.type());
        assertEquals(EconomyEvent.Severity.MINOR, event.severity());
        assertEquals(-3, save.economyIndex());
        assertEquals(82, save.economyReading());   // 85 - 3 (the wiki's flat -5%)
        assertEquals(Integer.valueOf(5), save.wage());
        assertEquals(200, save.bank());
        assertEquals(49, save.happiness());        // 50 - 1
        assertEquals(1, event.happinessLost());
    }

    @Test
    void moderateCrashCanCutPayToEightyPercent() {
        SaveState save = eventEligibleSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(10);

        // Crash 1; severity 2 -> MODERATE; fire coin 2 -> pay cut.
        EconomyEvent event = new EconomyService(rolls(1, 2, 2)).rollEvent(save);

        assertEquals(Integer.valueOf(8), event.wageCutTo());
        assertEquals(Integer.valueOf(8), save.wage());
        assertFalse(event.fired());
        assertEquals(48, save.happiness());        // -2
    }

    @Test
    void moderateCrashCanFireInstead() {
        SaveState save = eventEligibleSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(10);

        // Crash 1; severity 2; fire coin 1 -> fired.
        EconomyEvent event = new EconomyService(rolls(1, 2, 1)).rollEvent(save);

        assertTrue(event.fired());
        assertNull(save.jobId());
        assertNull(save.wage());
    }

    @Test
    void majorCrashFiresAndWipesTheBank() {
        SaveState save = eventEligibleSave();
        save.setJobId(TestSaves.COOK.id());
        save.setWage(10);
        save.setBank(500);

        // Crash 1; severity 3 -> MAJOR (no fire coin — firing is certain).
        EconomyEvent event = new EconomyService(rolls(1, 3)).rollEvent(save);

        assertTrue(event.fired());
        assertTrue(event.bankWiped());
        assertEquals(0, save.bank());
        assertNull(save.jobId());
        assertEquals(76, save.economyReading());   // 85 - 9
        assertEquals(47, save.happiness());        // -3
    }

    @Test
    void aMissedCrashRollStillAllowsABoomRoll() {
        SaveState save = eventEligibleSave();      // reading 85: crash-eligible

        // Crash roll 2 -> miss; boom roll 1 -> boom.
        EconomyEvent event = new EconomyService(rolls(2, 1)).rollEvent(save);

        assertEquals(EconomyEvent.Type.BOOM, event.type());
        assertEquals(3, save.economyIndex());
        assertEquals(90, save.economyReading());   // 85 + 6 clamped to 90
        assertEquals(50, save.happiness());        // no stocks yet: no boom bonus (wiki)
    }
}
