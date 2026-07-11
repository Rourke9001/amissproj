package amiss.application.service.save;

/**
 * The outcome of {@link ShopService#eat} (KAN-53), the save-scoped port of the legacy
 * {@code amiss.application.service.EatOutcome}.
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
         * Cash is below {@code price}; the meal was not bought, but the eat-time was still
         * charged (the game rule the legacy service kept: time is spent before cash is checked).
         */
        INSUFFICIENT_CASH
    }
}
