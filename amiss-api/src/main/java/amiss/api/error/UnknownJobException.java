package amiss.api.error;

/** Thrown when a job application names a job id that does not exist in {@code tbljob} (→ 400). */
public class UnknownJobException extends RuntimeException {

    public UnknownJobException(int jobId) {
        super("Unknown job id: " + jobId);
    }
}
