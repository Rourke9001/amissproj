package amiss.api.error;

/** Thrown when enrolling while already enrolled in a degree (→ 409). */
public class AlreadyEnrolledException extends RuntimeException {

    public AlreadyEnrolledException(String username) {
        super("Player '" + username + "' is already enrolled in a degree");
    }
}
