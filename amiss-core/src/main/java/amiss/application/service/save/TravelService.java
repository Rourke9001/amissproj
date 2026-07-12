package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.board.Board;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;

/**
 * Movement on the 13-stop board ring (KAN-53), the save-scoped port of the legacy
 * {@code TravelService}. A move costs {@code ringDistance x travel-per-step + enter-building}
 * minutes; the shortest direction around the loop is implied by {@link Board#ringDistance}.
 * Moving to the current stop is legal and charges only the entry cost. The new position is
 * persisted only when the move lands with time to spare; a stale saved position (not a stop)
 * is treated as home. A move that lands the clock exactly on zero is still charged and the
 * week ends there — the position does not change, since the coming rollover resets it to
 * home anyway.
 */
public class TravelService {

    private final SaveRepository saves;
    private final ActionCosts costs;
    private final Board board = new Board();

    public TravelService(SaveRepository saves, ActionCosts costs) {
        this.saves = saves;
        this.costs = costs;
    }

    /**
     * The stop {@code save} currently stands at. A saved position that is not a stop (e.g. a
     * stale pre-ring save) is reported as home, mirroring {@link #moveTo}'s clamp.
     */
    public Location currentLocation(SaveState save) {
        int row = save.xpos();
        int col = save.ypos();
        if (!board.isStop(row, col)) {
            int[] home = board.cellOf(0);
            row = home[0];
            col = home[1];
        }
        return board.locationAt(row, col);
    }

    /** Attempts to walk {@code save} to {@code target} and enter it. */
    public MoveResult moveTo(SaveState save, Location target) {
        int fromIndex = board.ringIndex(save.xpos(), save.ypos());
        if (fromIndex < 0) {
            fromIndex = 0; // stale/invalid saved cell: measure from home, like the Swing clamp
        }
        int steps = board.ringDistance(fromIndex, board.ringIndexOf(target));
        int cost = steps * costs.travelPerStepMinutes() + costs.enterBuildingMinutes();

        int time = save.timeMinutes();
        int remaining = time - cost;
        if (remaining < 0) {
            return new MoveResult(
                    time == 0 ? MoveResult.Status.WEEK_OVER : MoveResult.Status.INSUFFICIENT_TIME,
                    steps, 0, time);
        }

        save.setTimeMinutes(remaining);
        if (remaining > 0) {
            int[] cell = board.cellOf(board.ringIndexOf(target));
            save.setPos(cell[0], cell[1]);
        }
        saves.update(save);
        return new MoveResult(remaining == 0 ? MoveResult.Status.WEEK_OVER : MoveResult.Status.OK,
                steps, cost, remaining);
    }
}
