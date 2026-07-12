package amiss.application.service.save;

/**
 * The outcome of a {@link BankService#deposit}/{@link BankService#withdraw} (KAN-53), the
 * save-scoped port of the legacy {@code amiss.application.service.BankTransaction}.
 *
 * @param status {@link Status#OK} when the transfer happened; otherwise why it did not
 * @param cash   the save's cash after the attempt ({@code -1} when nothing was read, i.e.
 *               {@link Status#INVALID_AMOUNT})
 * @param bank   the save's bank balance after the attempt (same caveat as {@code cash})
 */
public record BankTransaction(Status status, int cash, int bank) {

    public enum Status {
        /** The transfer happened; both balances were updated. */
        OK,
        /** {@code amount} was not positive; nothing was checked or changed. */
        INVALID_AMOUNT,
        /** The source balance (cash for a deposit, bank for a withdrawal) was too low. */
        INSUFFICIENT_FUNDS,
        /** The week is over; nothing was checked or changed. */
        WEEK_OVER
    }
}
