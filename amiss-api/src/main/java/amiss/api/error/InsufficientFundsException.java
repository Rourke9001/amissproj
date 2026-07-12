package amiss.api.error;

/** Thrown when a bank transfer's source balance is too low (→ 409). */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(long saveId) {
        super("Save " + saveId + " does not have enough funds for this transfer");
    }
}
