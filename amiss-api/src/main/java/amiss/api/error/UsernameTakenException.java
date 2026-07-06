package amiss.api.error;

/** Thrown when {@code POST /api/auth/register} names a username that already has a stored password (→ 409). */
public class UsernameTakenException extends RuntimeException {

    private final String username;

    public UsernameTakenException(String username) {
        super("Username '" + username + "' is already taken");
        this.username = username;
    }

    public String username() {
        return username;
    }
}
