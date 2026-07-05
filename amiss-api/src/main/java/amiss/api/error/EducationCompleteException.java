package amiss.api.error;

/** Thrown when enrolling or studying after every degree is already complete (→ 409). */
public class EducationCompleteException extends RuntimeException {

    public EducationCompleteException(String username) {
        super("Player '" + username + "' has already completed every degree");
    }
}
