package amiss.application.service.save;

/**
 * The outcome of {@link RentService#payRent} (KAN-53), the save-scoped port of the legacy
 * {@code amiss.application.service.RentPayment}.
 *
 * @param status           {@link Status#OK} when rent was paid; otherwise why it was not
 * @param remainingMinutes the clock after the attempt
 * @param cash             the save's cash after the attempt
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
        INSUFFICIENT_TIME
    }
}
