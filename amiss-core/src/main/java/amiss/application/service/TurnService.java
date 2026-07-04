package amiss.application.service;

import amiss.application.config.ActionCosts;
import amiss.domain.board.Board;

/**
 * The week rollover rule, extracted from {@code MainGameGUI} so the Swing client and the REST
 * API share one tested implementation. There is no silent auto-rollover: a caller must ask for
 * {@link #endWeek()} explicitly, and it refuses while time remains on the clock.
 *
 * <p>Rollover, in order: settle the closed round (every 4th round with rent still unpaid
 * charges the late-rent debt — this now applies at every end-week, where the old Swing code
 * only charged it on re-login), consume one stored food to decide the new budget (fed weeks
 * are longer), reset the clock and the board position to home, advance the round, and flag
 * rent due when the new round is a rent round. Wages are never paid at rollover — they stay
 * per work action.
 */
public class TurnService {

    /** Rent falls due (and is settled) every Nth round. */
    private static final int RENT_ROUND_INTERVAL = 4;
    /** The late-rent charge applied when a rent round closes unpaid. */
    private static final int LATE_RENT_DEBT = 80;

    private final TimeService time;
    private final FoodService food;
    private final StatsService stats;
    private final ActionCosts costs;
    private final Board board = new Board();

    public TurnService(TimeService time, FoodService food, StatsService stats,
            ActionCosts costs) {
        this.time = time;
        this.food = food;
        this.stats = stats;
        this.costs = costs;
    }

    /**
     * Ends the current week and starts the next round, or refuses (changing nothing) while
     * time remains on the clock — see {@link WeekSummary#weekStillRunning()}.
     */
    public WeekSummary endWeek() {
        if (!time.spendMinutes(0).weekOver()) {
            return WeekSummary.weekStillRunning();
        }

        int closedRound = Integer.parseInt(time.getRound());
        boolean debtCharged = closedRound % RENT_ROUND_INTERVAL == 0 && stats.getRent() == 1;
        if (debtCharged) {
            stats.setDebt(LATE_RENT_DEBT);
        }

        boolean fed = food.getEat(); // consumes one stored food - must be called exactly once
        int week = fed ? costs.fedWeekMinutes() : costs.baseWeekMinutes();
        time.setTime(week);
        int[] home = board.cellOf(0);
        time.setPos(home[0], home[1]);
        time.setRound();

        int newRound = closedRound + 1;
        boolean rentDue = newRound % RENT_ROUND_INTERVAL == 0;
        if (rentDue) {
            stats.setRent(1);
        }
        return new WeekSummary(true, newRound, fed, week, rentDue, debtCharged);
    }
}
