package amiss.api.error;

/** Thrown when a bank transfer's source balance is too low (→ 409). */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String username) {
        super("Player '" + username + "' does not have enough funds for this transfer");
    }
}
