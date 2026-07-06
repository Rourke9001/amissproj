package amiss.application.service;

/**
 * The outcome of {@link StatsService#eat(int)} (was the body of {@code StatsService.eatMain(int)}).
 *
 * @param status           {@link Status#OK} when the meal was bought; otherwise why not
 * @param remainingMinutes the clock after the attempt
 * @param cash             cash after the attempt ({@code -1} when nothing was read, e.g. a
 *                         rejected time spend)
 * @param price            the price of the item being purchased
 */
public record EatOutcome(Status status, int remainingMinutes, int cash, int price) {

    public enum Status {
        /** Bought and ate: time, cash, food and happiness were updated. */
        OK,
        /** Eating would push the clock below zero; nothing was charged. */
        INSUFFICIENT_TIME,
        /**
         * Cash is below {@code price}; the meal was not bought, but the hour was still
         * charged (Swing charges time before checking cash — the game rule the API keeps).
         */
        INSUFFICIENT_CASH,
        /** A persistence failure prevented the purchase. */
        FAILED
    }
}
