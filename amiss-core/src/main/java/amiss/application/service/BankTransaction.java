package amiss.application.service;

/**
 * The outcome of a {@link BankService#deposit}/{@link BankService#withdraw}.
 *
 * @param status {@link Status#OK} when the transfer happened; otherwise why it did not
 * @param cash   the player's cash after the attempt (a fresh read; unspecified/-1 when no
 *               database call was made, i.e. {@link Status#INVALID_AMOUNT})
 * @param bank   the player's bank balance after the attempt (same caveat as {@code cash})
 */
public record BankTransaction(Status status, int cash, int bank) {

    public enum Status {
        /** The transfer happened; both balances were updated atomically. */
        OK,
        /** {@code amount} was not positive; nothing was checked or changed. */
        INVALID_AMOUNT,
        /** The source balance (cash for a deposit, bank for a withdrawal) was too low. */
        INSUFFICIENT_FUNDS,
        /** A persistence failure prevented the transfer. */
        FAILED
    }
}
