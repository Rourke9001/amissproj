package amiss.api.error;

/** Thrown when enrolling in a degree whose prerequisite has not been earned yet (→ 409). */
public class DegreeLockedException extends RuntimeException {

    public DegreeLockedException(int degreeId) {
        super("Degree " + degreeId + " is locked until its prerequisite is earned");
    }
}
