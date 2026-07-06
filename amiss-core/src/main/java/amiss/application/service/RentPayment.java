package amiss.application.service;

/**
 * The outcome of {@link RentService#payRent()}.
 *
 * @param status           {@link Status#OK} when rent was paid; otherwise why it was not
 * @param remainingMinutes the clock after the attempt
 * @param cash             the player's cash after the attempt
 */
public record RentPayment(Status status, int remainingMinutes, int cash) {

    public enum Status {
        /** Paid: {@link RentService#WEEKLY_RENT} was charged and the rent flag cleared. */
        OK,
        /** Rent is not currently owed (not a 4th round, or already paid this round). */
        NOT_DUE,
        /** The week is used up; nothing was charged. */
        WEEK_OVER,
        /** Cash is below {@link RentService#WEEKLY_RENT}; nothing was charged. */
        INSUFFICIENT_CASH,
        /** Paying would push the clock below zero; nothing was charged. */
        INSUFFICIENT_TIME,
        /** A persistence failure prevented the payment. */
        FAILED
    }
}
