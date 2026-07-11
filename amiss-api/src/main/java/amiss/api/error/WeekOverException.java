package amiss.api.error;

/**
 * Thrown when an action is attempted while the save's week is used up — the game state
 * conflicts (→ 409) until {@code POST .../end-week} starts the next round.
 */
public class WeekOverException extends RuntimeException {

    public WeekOverException(long saveId) {
        super("Save " + saveId + " has used up the week; end it via POST .../end-week");
    }
}
