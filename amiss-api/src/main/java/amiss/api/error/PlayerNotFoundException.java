package amiss.api.error;

/** Thrown when a request names a player that has no {@code tbluser} row. */
public class PlayerNotFoundException extends RuntimeException {

    private final String username;

    public PlayerNotFoundException(String username) {
        super("No player named '" + username + "'");
        this.username = username;
    }

    public String username() {
        return username;
    }
}
