package amiss.api.error;

/**
 * Thrown when {@code POST /api/auth/register} fails a {@code Validation} rule (→ 400). The
 * message names which rule failed but never echoes the submitted password.
 */
public class InvalidRegistrationException extends RuntimeException {

    public InvalidRegistrationException(String reason) {
        super(reason);
    }
}
