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
    /** Events only from the wiki's week 8. */
    static final int EVENT_MIN_ROUND = 8;
    /** A crash needs a near-peak economy (wiki: Reading >= 80). */
    static final int CRASH_MIN_READING = 80;
    /** Wiki CD-ROM odds at one player: 1/(1+30*players) = 1/31, each, per week. */
    static final int EVENT_CHANCE = 31;
    /** The wiki's flat -5/-10/-15% price drop, as Reading points (price% = reading/60). */
    static final int[] CRASH_READING_DROP = {3, 6, 9};
    static final int[] CRASH_HAPPINESS_LOSS = {1, 2, 3};
    /** The wiki's flat +10% boom bump, as Reading points. */
    static final int BOOM_READING_JUMP = 6;

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
        save.setEconomyIndex((byte) index);
        save.setEconomyReading((short) reading);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Rolls for a crash/boom after the weekly drift. Call with the round already
     * advanced. Crash is checked first (only when the Reading is at least
     * {@link #CRASH_MIN_READING}); a missed or ineligible crash still allows the
     * boom roll — the wiki's boom bound (Reading <= 120) exceeds the +90 cap, so
     * booms are always eligible.
     */
    public EconomyEvent rollEvent(SaveState save) {
        if (save.round() < EVENT_MIN_ROUND) {
            return EconomyEvent.none();
        }
        if (save.economyReading() >= CRASH_MIN_READING && roll1toN.applyAsInt(EVENT_CHANCE) == 1) {
            return crash(save);
        }
        if (roll1toN.applyAsInt(EVENT_CHANCE) == 1) {
            return boom(save);
        }
        return EconomyEvent.none();
    }

    private EconomyEvent crash(SaveState save) {
        int severityIndex = roll1toN.applyAsInt(3) - 1;
        EconomyEvent.Severity severity = EconomyEvent.Severity.values()[severityIndex];
        save.setEconomyIndex((byte) INDEX_MIN);
        save.setEconomyReading((short) clamp(save.economyReading() - CRASH_READING_DROP[severityIndex],
                READING_MIN, READING_MAX));

        boolean fired = false;
        Integer wageCutTo = null;
        if (severity == EconomyEvent.Severity.MODERATE && save.employed()) {
            if (roll1toN.applyAsInt(2) == 1) {
                fired = fire(save);
            } else {
                wageCutTo = save.wage() * 4 / 5;
                save.setWage(wageCutTo);
            }
        }
        boolean bankWiped = false;
        if (severity == EconomyEvent.Severity.MAJOR) {
            if (save.employed()) {
                fired = fire(save);
            }
            bankWiped = save.bank() > 0;
            save.setBank(0);
        }
        int happinessLost = CRASH_HAPPINESS_LOSS[severityIndex];
        save.addHappiness(-happinessLost);
        return new EconomyEvent(EconomyEvent.Type.CRASH, severity, fired, wageCutTo,
                bankWiped, happinessLost);
    }

    private boolean fire(SaveState save) {
        save.setJobId(null);
        save.setWage(null);
        return true;
    }

    private EconomyEvent boom(SaveState save) {
        save.setEconomyIndex((byte) INDEX_MAX);
        save.setEconomyReading((short) clamp(save.economyReading() + BOOM_READING_JUMP,
                READING_MIN, READING_MAX));
        return new EconomyEvent(EconomyEvent.Type.BOOM, null, false, null, false, 0);
    }
}
