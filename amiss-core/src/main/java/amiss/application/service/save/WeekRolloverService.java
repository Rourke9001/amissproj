package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.board.Board;
import amiss.domain.model.SaveState;

/**
 * The week rollover for a save (KAN-53) — the new-layer TurnService, plus the wiki's
 * turn-start rules. In order: refuse while time remains; settle the closed round
 * (every 4th round with rent unpaid charges the R80 late-rent debt); consume one
 * stored food to size the new week; reset clock and board position; advance the
 * round (flagging rent due); decay dependability −3 (floor 0); apply the unfed
 * starvation penalty and resolve a possible Doctor Visit; then drift the economy;
 * then the win check — all four goals met at the start of a turn wins, and
 * {@code won} is sticky.
 */
public class WeekRolloverService {

    private static final int RENT_ROUND_INTERVAL = 4;
    private static final int LATE_RENT_DEBT = 80;
    private static final int WEEKLY_DEPENDABILITY_DECAY = 3;
    private static final int STARVATION_HAPPINESS_LOSS = 2;

    /** What happened at rollover; {@code rolled} false = the week wasn't over. */
    public record RolloverResult(boolean rolled, int newRound, boolean fed, int weekMinutes,
            boolean rentDue, boolean debtCharged, boolean won, EconomyEvent economy,
            DoctorVisitOutcome doctorVisit) {

        static RolloverResult weekStillRunning() {
            return new RolloverResult(false, -1, false, -1, false, false, false,
                    EconomyEvent.none(), DoctorVisitOutcome.none());
        }
    }

    private final SaveRepository saves;
    private final GoalService goals;
    private final ActionCosts costs;
    private final EconomyService economy;
    private final DoctorVisitService doctorVisit;
    private final Board board = new Board();

    public WeekRolloverService(SaveRepository saves, GoalService goals, ActionCosts costs,
            EconomyService economy, DoctorVisitService doctorVisit) {
        this.saves = saves;
        this.goals = goals;
        this.costs = costs;
        this.economy = economy;
        this.doctorVisit = doctorVisit;
    }

    public RolloverResult endWeek(SaveState save) {
        if (!save.weekOver()) {
            return RolloverResult.weekStillRunning();
        }

        int closedRound = save.round();
        boolean debtCharged = closedRound % RENT_ROUND_INTERVAL == 0 && save.rent() == 1;
        if (debtCharged) {
            save.setDebt(save.debt() + LATE_RENT_DEBT);
        }

        boolean fed = save.eat() > 0;
        if (fed) {
            save.setEat(save.eat() - 1);
        }
        save.setTimeMinutes(costs.baseWeekMinutes());
        if (!fed) {
            save.spendUpTo(costs.starvationPenaltyMinutes());
            save.addHappiness(-STARVATION_HAPPINESS_LOSS);
        }
        int[] home = board.cellOf(0);
        save.setPos(home[0], home[1]);

        int newRound = closedRound + 1;
        save.setRound(newRound);
        boolean rentDue = newRound % RENT_ROUND_INTERVAL == 0;
        if (rentDue) {
            save.setRent(1);
        }

        save.setDependability(Math.max(0, save.dependability() - WEEKLY_DEPENDABILITY_DECAY));

        DoctorVisitOutcome doctorVisitOutcome = doctorVisit.resolve(save, !fed);

        economy.driftWeekly(save);
        EconomyEvent economyEvent = economy.rollEvent(save);

        boolean wonNow = save.won() || goals.progress(save).allMet();
        save.setWon(wonNow);

        saves.update(save);
        return new RolloverResult(true, newRound, fed, save.timeMinutes(), rentDue,
                debtCharged, wonNow, economyEvent, doctorVisitOutcome);
    }
}
