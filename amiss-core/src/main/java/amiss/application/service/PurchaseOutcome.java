package amiss.application.service;

/**
 * The outcome of {@link StatsService#buyGroceries(int, int)} /
 * {@link StatsService#buyClothes(int, int)} (was the body of {@code MarketGUI}'s and
 * {@code ClothesStoreGUI}'s purchase handlers).
 *
 * @param status           {@link Status#OK} when the purchase happened; otherwise why not
 * @param remainingMinutes the clock after the attempt
 * @param cash             cash after the attempt
 */
public record PurchaseOutcome(Status status, int remainingMinutes, int cash) {

    public enum Status {
        /** Bought: time and cash were charged. */
        OK,
        /** The week is used up; nothing was charged. */
        WEEK_OVER,
        /** Cash is below the item's price; nothing was charged. */
        INSUFFICIENT_CASH,
        /** Buying would push the clock below zero; nothing was charged. */
        INSUFFICIENT_TIME,
        /** A persistence failure prevented the purchase. */
        FAILED
    }
}
