package amiss.application.service;

import amiss.application.config.ActionCosts;
import amiss.domain.board.Board;
import amiss.domain.board.Location;

/**
 * Movement on the 13-stop board ring, shared by the Swing client and the REST API
 * (extracted from the {@code MainGameGUI} click handler). A move costs
 * {@code ringDistance x travel-per-step + enter-building} minutes; the shortest direction
 * around the loop is implied by {@link Board#ringDistance}. Moving to the current stop is
 * legal and charges only the entry cost. The new position is persisted only when the move
 * fully succeeds; a stale saved position (not a stop) is treated as home.
 */
public class TravelService {

    private final TimeService time;
    private final ActionCosts costs;
    private final Board board = new Board();

    public TravelService(TimeService time, ActionCosts costs) {
        this.time = time;
        this.costs = costs;
    }

    /** Attempts to walk to {@code target} and enter it. */
    public MoveResult moveTo(Location target) {
        int fromIndex = board.ringIndex(time.getX(), time.getY());
        if (fromIndex < 0) {
            fromIndex = 0; // stale/invalid saved cell: measure from home, like the Swing clamp
        }
        int steps = board.ringDistance(fromIndex, board.ringIndexOf(target));
        int cost = steps * costs.travelPerStepMinutes() + costs.enterBuildingMinutes();

        TimeSpend spend = time.spendMinutes(cost);
        if (spend.rejected()) {
            return new MoveResult(
                    spend.weekOver() ? MoveResult.Status.WEEK_OVER : MoveResult.Status.INSUFFICIENT_TIME,
                    steps, 0, spend.remainingMinutes());
        }
        if (spend.weekOver()) {
            // The walk consumed the last minutes: the week ends on the road (Swing parity) -
            // charged, but the position stays put and the new week starts from home.
            return new MoveResult(MoveResult.Status.WEEK_OVER, steps, cost, 0);
        }

        int[] cell = board.cellOf(board.ringIndexOf(target));
        time.setPos(cell[0], cell[1]);
        return new MoveResult(MoveResult.Status.OK, steps, cost, spend.remainingMinutes());
    }
}
