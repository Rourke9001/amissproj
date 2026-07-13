package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        save.setEconomyReading(reading);
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
        save.setEconomyIndex(3);
        // Roll 3 -> +1 clamps at +3; noise roll 11 -> +5; reading 90 + 30 + 5 clamps at 90.
        new EconomyService(rolls(3, 11)).driftWeekly(save);
        assertEquals(3, save.economyIndex());
        assertEquals(90, save.economyReading());
    }

    @Test
    void driftClampsAtTheFloorToo() {
        SaveState save = saveWithReading(-30);
        save.setEconomyIndex(-3);
        // Roll 1 -> -1 clamps at -3; noise roll 1 -> -5; reading floor holds.
        new EconomyService(rolls(1, 1)).driftWeekly(save);
        assertEquals(-3, save.economyIndex());
        assertEquals(-30, save.economyReading());
    }
}
