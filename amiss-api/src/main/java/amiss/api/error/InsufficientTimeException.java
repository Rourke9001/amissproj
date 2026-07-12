package amiss.api.error;

/** Thrown when an action costs more minutes than the save has left (→ 409). */
public class InsufficientTimeException extends RuntimeException {

    public InsufficientTimeException(long saveId) {
        super("Save " + saveId + " does not have enough time left for this action");
    }
}
