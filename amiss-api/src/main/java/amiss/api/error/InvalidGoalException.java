package amiss.api.error;

/** Thrown when {@code POST /api/saves}'s goal targets fail the 10-100 range rule (→ 400). */
public class InvalidGoalException extends RuntimeException {

    public InvalidGoalException(String reason) {
        super(reason);
    }
}
