package amiss.api.error;

/** Thrown when a job application names a job that does not exist in {@code tbljobs} (→ 400). */
public class UnknownJobException extends RuntimeException {

    public UnknownJobException(String jobName) {
        super("Unknown job: '" + jobName + "'");
    }
}
