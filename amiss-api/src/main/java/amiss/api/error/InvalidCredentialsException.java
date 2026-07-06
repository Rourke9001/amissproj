package amiss.api.error;

/**
 * Thrown by {@code POST /api/auth/login} for either an unknown username or a wrong password
 * (→ 401). Deliberately one generic message for both cases, so the response never reveals
 * which of the two was the actual problem.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
