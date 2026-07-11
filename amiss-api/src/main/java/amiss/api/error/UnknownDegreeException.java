package amiss.api.error;

/** Thrown when enrolling names a degree id that does not exist in {@code tbldegrees} (→ 400). */
public class UnknownDegreeException extends RuntimeException {

    public UnknownDegreeException(int degreeId) {
        super("Unknown degree id: " + degreeId);
    }
}
