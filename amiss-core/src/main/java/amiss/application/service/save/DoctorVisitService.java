package amiss.application.service.save;

import amiss.domain.model.SaveState;
import java.util.function.IntUnaryOperator;

/**
 * The shared Doctor Visit event (KAN-23, wiki Doctor Visit page). Three independent
 * conditions can trigger it — Starvation (25%), spoiled fridgeless fresh food (50%),
 * a Relaxation stat at its floor (20%, PR 4) — each rolled separately when its
 * condition is true; only one visit resolves per turn even if more than one condition
 * fires. Effect: +10h, -4 happiness, and a cash cost tiered by cash on hand. The event
 * is bypassed entirely if the player has $0 cash (wiki-exact). Rolls come through the
 * injected {@code roll1toN} (uniform 1..n) so every outcome is deterministic under test.
 */
public class DoctorVisitService {

    private static final int STARVATION_CHANCE = 4;    // 1-in-4 = 25%
    private static final int SPOILAGE_CHANCE = 2;       // 1-in-2 = 50%
    private static final int RELAXATION_CHANCE = 5;    // 1-in-5 = 20%
    private static final int MINUTES_LOST = 600;       // 10h
    private static final int HAPPINESS_LOST = 4;
    private static final int HIGH_CASH_THRESHOLD = 500;
    private static final int MID_CASH_THRESHOLD = 50;
    private static final int LOW_CASH_THRESHOLD = 31;
    private static final int HIGH_TIER_WIDTH = 171;    // 30..200 inclusive
    private static final int MID_TIER_WIDTH = 21;      // 30..50 inclusive
    private static final int TIER_FLOOR = 30;

    private final IntUnaryOperator roll1toN;

    public DoctorVisitService(IntUnaryOperator roll1toN) {
        this.roll1toN = roll1toN;
    }

    public DoctorVisitOutcome resolve(SaveState save, boolean starved, boolean spoiledFreshFood,
            boolean relaxationAtFloor) {
        if (save.cash() <= 0) {
            return DoctorVisitOutcome.none();
        }
        boolean starvationHit = starved && roll1toN.applyAsInt(STARVATION_CHANCE) == 1;
        boolean spoilageHit = spoiledFreshFood && roll1toN.applyAsInt(SPOILAGE_CHANCE) == 1;
        boolean relaxationHit = relaxationAtFloor && roll1toN.applyAsInt(RELAXATION_CHANCE) == 1;
        boolean triggered = starvationHit || spoilageHit || relaxationHit;
        if (!triggered) {
            return DoctorVisitOutcome.none();
        }
        int cost = cost(save.cash());
        save.setCash(save.cash() - cost);
        save.spendUpTo(MINUTES_LOST);
        save.addHappiness(-HAPPINESS_LOST);
        return new DoctorVisitOutcome(true, MINUTES_LOST, HAPPINESS_LOST, cost);
    }

    private int cost(int cash) {
        if (cash >= HIGH_CASH_THRESHOLD) {
            return TIER_FLOOR - 1 + roll1toN.applyAsInt(HIGH_TIER_WIDTH);
        }
        if (cash >= MID_CASH_THRESHOLD) {
            return TIER_FLOOR - 1 + roll1toN.applyAsInt(MID_TIER_WIDTH);
        }
        if (cash >= LOW_CASH_THRESHOLD) {
            return TIER_FLOOR - 1 + roll1toN.applyAsInt(cash - TIER_FLOOR + 1);
        }
        return cash;
    }
}
