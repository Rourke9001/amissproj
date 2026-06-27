package amiss.service;

/**
 * The outcome of a player action, expressed as plain values for a screen to render.
 *
 * <p>Returned by the orchestration methods {@link StatsService#workMain()} and
 * {@link StatsService#eatMain(int)} so the game rules never touch Swing. {@code message}
 * is the text to append to the notification area (it already carries the leading
 * newline(s) the screen expects). {@code timer} / {@code money} are new label texts, or
 * {@code null} to leave that label unchanged.
 */
public final class ActionResult {

    private final String message;
    private final String timer;
    private final String money;

    public ActionResult(String message, String timer, String money) {
        this.message = message;
        this.timer = timer;
        this.money = money;
    }

    /** A result that only appends {@code message}, leaving the timer and money labels as they are. */
    public static ActionResult message(String message) {
        return new ActionResult(message, null, null);
    }

    /** Text to append to the notification area (includes its own leading newline(s)). */
    public String message() {
        return message;
    }

    /** New timer-label text, or {@code null} to leave it unchanged. */
    public String timer() {
        return timer;
    }

    /** New money-label text, or {@code null} to leave it unchanged. */
    public String money() {
        return money;
    }
}
