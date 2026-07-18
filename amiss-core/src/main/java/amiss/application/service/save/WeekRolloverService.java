package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.board.Board;
import amiss.domain.model.ApplianceItem;
import amiss.domain.model.SaveState;

/**
 * The week rollover for a save (KAN-53) — the new-layer TurnService, plus the wiki's
 * turn-start rules. In order: refuse while time remains; settle the closed round
 * (every 4th round with rent unpaid charges the R80 late-rent debt); size the new
 * week's food — stored fresh food feeds it (fast food only ever feeds the turn just
 * spent, and is cleared here), fridgeless fresh food then spoils to zero while a
 * Fridge owner's stock decays by one instead; reset clock and board position; advance
 * the round (flagging rent due); decay dependability −3 (floor 0); decay each clothing
 * category's remaining weeks (floor 0); reset the first-relax flag and decay Relaxation
 * by 1 (floor 10) and resolve a possible Doctor Visit (starvation, spoilage, or a
 * Relaxation stat at its floor can each trigger it, at most one visit per turn); then
 * drift the economy; then the win check — all four goals met at the start of a turn
 * wins, and {@code won} is sticky.
 */
public class WeekRolloverService {

    private static final int RENT_ROUND_INTERVAL = 4;
    private static final int LATE_RENT_DEBT = 80;
    private static final int WEEKLY_DEPENDABILITY_DECAY = 3;
    private static final int STARVATION_HAPPINESS_LOSS = 2;
    private static final int RELAXATION_FLOOR = 10;

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

        boolean hadFreshFood = save.eat() > 0;
        boolean fed = save.ateFastFoodLastTurn() || hadFreshFood;
        boolean spoiledFreshFood = !save.owns(ApplianceItem.FRIDGE) && hadFreshFood;
        if (spoiledFreshFood) {
            save.setEat(0);
        } else if (hadFreshFood) {
            save.setEat(save.eat() - 1);
        }
        save.setAteFastFoodLastTurn(false);
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

        save.setCasualWeeks(Math.max(0, save.casualWeeks() - 1));
        save.setDressWeeks(Math.max(0, save.dressWeeks() - 1));
        save.setBusinessWeeks(Math.max(0, save.businessWeeks() - 1));

        save.setRelaxedThisTurn(false);
        save.addRelaxation(-1);
        boolean relaxationAtFloor = save.relaxation() <= RELAXATION_FLOOR;

        DoctorVisitOutcome doctorVisitOutcome = doctorVisit.resolve(save, !fed, spoiledFreshFood, relaxationAtFloor);

        economy.driftWeekly(save);
        EconomyEvent economyEvent = economy.rollEvent(save);

        boolean wonNow = save.won() || goals.progress(save).allMet();
        save.setWon(wonNow);

        saves.update(save);
        return new RolloverResult(true, newRound, fed, save.timeMinutes(), rentDue,
                debtCharged, wonNow, economyEvent, doctorVisitOutcome);
    }
}
