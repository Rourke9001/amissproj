package amiss.api.web;

import amiss.api.error.WrongLocationException;
import amiss.application.service.save.TravelService;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;

/**
 * Enforces that a mutating action endpoint is only reachable from its building's stop —
 * the save-scoped port (KAN-54) of the per-username original.
 */
final class LocationGuard {

    private LocationGuard() {
    }

    static void requireAt(TravelService travel, SaveState save, Location required) {
        Location actual = travel.currentLocation(save);
        if (actual != required) {
            throw new WrongLocationException(required, actual);
        }
    }
}
