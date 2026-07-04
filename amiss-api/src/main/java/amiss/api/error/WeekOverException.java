package amiss.api.error;

/**
 * Thrown when an action is attempted while the player's week is used up — the game state
 * conflicts (→ 409) until {@code POST .../end-week} starts the next round.
 */
public class WeekOverException extends RuntimeException {

    public WeekOverException(String username) {
        super("Player '" + username + "' has used up the week; end it via POST .../end-week");
    }
}
