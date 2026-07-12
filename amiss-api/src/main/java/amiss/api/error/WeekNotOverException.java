package amiss.api.error;

/**
 * Thrown when {@code POST .../end-week} is called while time remains on the save's clock —
 * there is no silent rollover, so the request conflicts with the game state (→ 409).
 */
public class WeekNotOverException extends RuntimeException {

    public WeekNotOverException(long saveId) {
        super("Save " + saveId + " still has time left this week");
    }
}
