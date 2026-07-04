package amiss.api.error;

/** Thrown when rent is paid while it is not currently owed (→ 409). */
public class RentNotDueException extends RuntimeException {

    public RentNotDueException(String username) {
        super("Player '" + username + "' does not currently owe rent");
    }
}
