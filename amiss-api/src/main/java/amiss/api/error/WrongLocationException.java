package amiss.api.error;

import amiss.domain.board.Location;

/** Thrown when an action requires the player to be standing at a specific stop (→ 409). */
public class WrongLocationException extends RuntimeException {

    public WrongLocationException(Location required, Location actual) {
        super("This action requires being at " + required.displayName()
                + ", but the player is at " + actual.displayName());
    }

    /**
     * For guards where the required stop is a free-text value (e.g. a job's
     * {@code tbljobs.location}) rather than a fixed {@link Location} constant.
     */
    public WrongLocationException(String required, Location actual) {
        super("This action requires being at " + required
                + ", but the player is at " + actual.displayName());
    }
}
