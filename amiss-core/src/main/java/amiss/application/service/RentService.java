package amiss.application.service;

import amiss.application.config.ActionCosts;
import amiss.domain.validation.Validation;

/**
 * Paying rent (was the body of {@code RentOfficeGUI.btnRentActionPerformed}). Rent is due on
 * every 4th round until paid; Swing's rent button is only shown then, so the {@link
 * RentPayment.Status#NOT_DUE} guard below is new for the API (which has no button
 * visibility to rely on) and is never reached from Swing. Every other check mirrors the
 * Swing handler exactly, in the same order, so the two clients charge identically.
 */
public class RentService {

    /** Rent charged per due round (matches {@code TurnService.LATE_RENT_DEBT}, the same
     * figure charged as debt when rent lapses at end-week). */
    public static final int WEEKLY_RENT = 80;

    private final TimeService time;
    private final StatsService stats;
    private final ActionCosts costs;

    public RentService(TimeService time, StatsService stats, ActionCosts costs) {
        this.time = time;
        this.stats = stats;
        this.costs = costs;
    }

    public RentPayment payRent() {
        int round = Validation.parseIntOrDefault(time.getRound(), -1);
        if (round % 4 != 0 || stats.getRent() != 1) {
            return new RentPayment(RentPayment.Status.NOT_DUE,
                    time.spendMinutes(0).remainingMinutes(), stats.getCash());
        }

        TimeSpend clock = time.spendMinutes(0);
        if (clock.weekOver()) {
            return new RentPayment(RentPayment.Status.WEEK_OVER, clock.remainingMinutes(), stats.getCash());
        }

        int cash = stats.getCash();
        if (cash < WEEKLY_RENT) {
            return new RentPayment(RentPayment.Status.INSUFFICIENT_CASH, clock.remainingMinutes(), cash);
        }

        TimeSpend spend = time.spendMinutes(costs.payRentMinutes());
        if (spend.rejected()) {
            return new RentPayment(RentPayment.Status.INSUFFICIENT_TIME, spend.remainingMinutes(), cash);
        }

        stats.setRent(0);
        stats.buy(Integer.toString(WEEKLY_RENT));
        return new RentPayment(RentPayment.Status.OK, spend.remainingMinutes(), stats.getCash());
    }
}
