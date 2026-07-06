package amiss.api.error;

/** Thrown when studying is attempted while not enrolled in a degree (→ 409). */
public class NotEnrolledException extends RuntimeException {

    public NotEnrolledException(String username) {
        super("Player '" + username + "' is not enrolled in a degree");
    }
}
