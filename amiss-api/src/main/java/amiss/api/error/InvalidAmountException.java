package amiss.api.error;

/** Thrown when a bank transfer names a non-positive amount (→ 400). */
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(int amount) {
        super("Amount must be positive: " + amount);
    }
}
