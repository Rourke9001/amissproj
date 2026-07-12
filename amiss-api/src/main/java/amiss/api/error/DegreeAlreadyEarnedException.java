package amiss.api.error;

/** Thrown when enrolling in a degree the save has already earned (→ 409). */
public class DegreeAlreadyEarnedException extends RuntimeException {

    public DegreeAlreadyEarnedException(int degreeId) {
        super("Degree " + degreeId + " has already been earned");
    }
}
