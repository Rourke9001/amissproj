package amiss.api.error;

/** Thrown when an action costs more minutes than the player has left (→ 409). */
public class InsufficientTimeException extends RuntimeException {

    public InsufficientTimeException(String username) {
        super("Player '" + username + "' does not have enough time left for this action");
    }
}
