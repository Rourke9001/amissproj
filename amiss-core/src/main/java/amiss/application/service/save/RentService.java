package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;

/**
 * Paying rent (KAN-53), the save-scoped port of the legacy {@code RentService}. Rent is due on
 * every 4th round until paid; the {@link RentPayment.Status#NOT_DUE} guard exists for the API
 * (which, unlike Swing's rent-button visibility, has no UI state to lean on).
 */
public class RentService {

    /** Rent charged per due round (matches {@code WeekRolloverService}'s late-rent debt). */
    public static final int WEEKLY_RENT = 80;
    private static final int RENT_ROUND_INTERVAL = 4;

    private final SaveRepository saves;
    private final ActionCosts costs;

    public RentService(SaveRepository saves, ActionCosts costs) {
        this.saves = saves;
        this.costs = costs;
    }

    public RentPayment payRent(SaveState save) {
        boolean due = save.round() % RENT_ROUND_INTERVAL == 0 && save.rent() == 1;
        if (!due) {
            return new RentPayment(RentPayment.Status.NOT_DUE, save.timeMinutes(), save.cash());
        }
        if (save.weekOver()) {
            return new RentPayment(RentPayment.Status.WEEK_OVER, save.timeMinutes(), save.cash());
        }

        int cash = save.cash();
        if (cash < WEEKLY_RENT) {
            return new RentPayment(RentPayment.Status.INSUFFICIENT_CASH, save.timeMinutes(), cash);
        }

        int time = save.timeMinutes();
        int remaining = time - costs.payRentMinutes();
        if (remaining < 0) {
            return new RentPayment(RentPayment.Status.INSUFFICIENT_TIME, time, cash);
        }

        save.setTimeMinutes(remaining);
        save.setRent(0);
        save.setCash(cash - WEEKLY_RENT);
        saves.update(save);
        return new RentPayment(RentPayment.Status.OK, remaining, save.cash());
    }
}
