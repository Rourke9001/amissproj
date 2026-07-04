package amiss.api.error;

/** Thrown when a move names a target that is not a board {@code Location} id (→ 400). */
public class UnknownLocationException extends RuntimeException {

    public UnknownLocationException(String target) {
        super("Unknown board location: '" + target + "'");
    }
}
