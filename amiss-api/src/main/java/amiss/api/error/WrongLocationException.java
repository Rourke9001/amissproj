package amiss.api.error;

import amiss.domain.board.Location;

/** Thrown when an action requires the player to be standing at a specific stop (→ 409). */
public class WrongLocationException extends RuntimeException {

    public WrongLocationException(Location required, Location actual) {
        super("This action requires being at " + required.displayName()
                + ", but the player is at " + actual.displayName());
    }
}
