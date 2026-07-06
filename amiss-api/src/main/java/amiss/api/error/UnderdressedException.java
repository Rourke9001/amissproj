package amiss.api.error;

/** Thrown when working is attempted without meeting the job's dress code (→ 409). */
public class UnderdressedException extends RuntimeException {

    public UnderdressedException(String username) {
        super("Player '" + username + "' is not properly dressed for this job");
    }
}
