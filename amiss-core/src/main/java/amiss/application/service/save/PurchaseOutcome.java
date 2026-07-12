package amiss.application.service.save;

/**
 * The outcome of {@link ShopService#buyGroceries}/{@link ShopService#buyClothes} (KAN-53),
 * the save-scoped port of the legacy {@code amiss.application.service.PurchaseOutcome}.
 *
 * @param status           {@link Status#OK} when the purchase happened; otherwise why not
 * @param remainingMinutes the clock after the attempt
 * @param cash             cash after the attempt
 */
public record PurchaseOutcome(Status status, int remainingMinutes, int cash) {

    public enum Status {
        /** Bought: time and cash were charged. */
        OK,
        /** The week is used up; nothing was charged (clothes only — checked before any spend). */
        WEEK_OVER,
        /**
         * Cash is below the item's price; the purchase did not happen. For clothes this is
         * checked before any time is spent, so nothing is charged; for groceries the shop-time
         * is spent first (mirroring the legacy order), so the clock may already show it.
         */
        INSUFFICIENT_CASH,
        /** Buying would push the clock below zero; nothing was charged. */
        INSUFFICIENT_TIME
    }
}
