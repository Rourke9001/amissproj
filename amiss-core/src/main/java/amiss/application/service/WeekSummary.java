package amiss.application.service;

/**
 * The outcome of {@link TurnService#endWeek()}.
 *
 * @param weekEnded   {@code false} if the rollover was refused because time remained on the
 *                    clock — nothing was changed
 * @param round       the new round number
 * @param fed         whether the player had stored food last week (fed players start the new
 *                    week with the larger budget)
 * @param timeMinutes the new week's time budget, in minutes
 * @param rentDue     {@code true} if the new round is a rent round (every 4th) — rent has been
 *                    flagged as due
 * @param debtCharged {@code true} if the closed round was a rent round with rent still unpaid,
 *                    so the late-rent debt was charged
 */
public record WeekSummary(boolean weekEnded, int round, boolean fed, int timeMinutes,
        boolean rentDue, boolean debtCharged) {

    /** The refusal result: time remains on the clock, so the week cannot be ended yet. */
    public static WeekSummary weekStillRunning() {
        return new WeekSummary(false, 0, false, 0, false, false);
    }
}
