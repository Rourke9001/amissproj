package amiss.api.error;

/** Thrown when working is attempted without meeting the job's dress code (→ 409). */
public class UnderdressedException extends RuntimeException {

    public UnderdressedException(long saveId) {
        super("Save " + saveId + " is not properly dressed for this job");
    }
}
