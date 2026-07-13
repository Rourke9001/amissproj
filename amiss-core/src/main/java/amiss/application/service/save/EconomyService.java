package amiss.application.service.save;

import amiss.domain.model.SaveState;
import java.util.function.IntUnaryOperator;

/**
 * The hidden per-save economy (KAN-48), wiki-exact where the wiki documents it. Two
 * persisted values drive every price: the Index (trend, -3..+3) and the Reading
 * (-30..+90); a displayed/charged price is {@code base + base*Reading/60} — 50% to
 * 250% of base. Base prices in the catalogs are never mutated; this service is the
 * single authority both charge paths and catalog reads consult, so the price a player
 * sees is the price they pay. Rolls come through the injected {@code roll1toN}
 * (uniform 1..n) so every outcome is deterministic under test.
 */
public class EconomyService {

    static final int INDEX_MIN = -3;
    static final int INDEX_MAX = 3;
    static final int READING_MIN = -30;
    static final int READING_MAX = 90;
    /** Our drift tuning (the wiki declines to document the original formula). */
    static final int READING_STEP_PER_INDEX = 10;

    private final IntUnaryOperator roll1toN;

    public EconomyService(IntUnaryOperator roll1toN) {
        this.roll1toN = roll1toN;
    }

    /** The economy-adjusted price of {@code base} for this save. Integer, floor-rounded. */
    public int price(int base, SaveState save) {
        return base + Math.floorDiv(base * save.economyReading(), 60);
    }

    /** One week of Index/Reading drift; Task 3 wires this into the rollover. */
    public void driftWeekly(SaveState save) {
        int index = clamp(save.economyIndex() + roll1toN.applyAsInt(3) - 2, INDEX_MIN, INDEX_MAX);
        int reading = clamp(save.economyReading() + READING_STEP_PER_INDEX * index
                + roll1toN.applyAsInt(11) - 6, READING_MIN, READING_MAX);
        save.setEconomyIndex(index);
        save.setEconomyReading(reading);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
