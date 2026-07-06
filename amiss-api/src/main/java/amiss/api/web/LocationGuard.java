package amiss.api.web;

import amiss.api.error.WrongLocationException;
import amiss.application.service.GameServices;
import amiss.domain.board.Location;

/**
 * Enforces that a mutating action endpoint is only reachable from its building's stop —
 * Swing already enforces this by only showing an action's screen once the player has
 * navigated there.
 */
final class LocationGuard {

    private LocationGuard() {
    }

    static void requireAt(GameServices services, Location required) {
        Location actual = services.travel().currentLocation();
        if (actual != required) {
            throw new WrongLocationException(required, actual);
        }
    }
}
