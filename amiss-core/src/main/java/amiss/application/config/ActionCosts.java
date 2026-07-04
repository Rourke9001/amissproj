package amiss.application.config;

/**
 * The time price list for every player action, in <strong>integer minutes</strong> — the
 * game's one clock unit. Time is a spendable resource like money, so it is never stored or
 * computed as floating point: binary doubles cannot represent fractions like 0.3h exactly,
 * which would make the {@code == 0} week-over checks unreliable.
 *
 * <p>Defaults live here ({@link #defaults()}); deployments override them without a rebuild —
 * the Swing client via {@code Config} (env {@code AMISS_COSTS_*} &gt; application.properties),
 * the REST API via Spring {@code @ConfigurationProperties(prefix = "amiss.costs")}.
 *
 * <p>Reference values (from the Jones-in-the-Fast-Lane design notes): a 60-hour week
 * (72 if fed), 2h to enter a building, 40 min per ring step so the longest walk
 * (6 steps) is 4h — "cross-town ≈ 4h".
 */
public record ActionCosts(
        int workMinutes,
        int studyMinutes,
        int relaxMinutes,
        int applyJobMinutes,
        int payRentMinutes,
        int eatMinutes,
        int shopMinutes,
        int travelPerStepMinutes,
        int enterBuildingMinutes,
        int baseWeekMinutes,
        int fedWeekMinutes) {

    /** The built-in cost table; used wherever no configuration override is supplied. */
    public static ActionCosts defaults() {
        return new ActionCosts(360, 360, 360, 240, 120, 60, 0, 40, 120, 3600, 4320);
    }
}
