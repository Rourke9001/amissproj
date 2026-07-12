package amiss.api.error;

/** Thrown when enrolling while already enrolled in a degree (→ 409). */
public class AlreadyEnrolledException extends RuntimeException {

    public AlreadyEnrolledException(long saveId) {
        super("Save " + saveId + " is already enrolled in a degree");
    }
}
