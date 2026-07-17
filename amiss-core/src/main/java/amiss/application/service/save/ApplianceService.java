package amiss.application.service.save;

import amiss.application.port.SaveRepository;
import amiss.domain.model.ApplianceItem;
import amiss.domain.model.SaveState;

/**
 * Buying an appliance/book (KAN-23) — minimal ownership plumbing: one canonical price
 * per item (no new/used variants, no break/repair — KAN-58), a one-time happiness bonus
 * the first time a save ever owns a given item, and purchases cost no time (matches
 * every other shop in the game).
 */
public class ApplianceService {

    private final SaveRepository saves;
    private final EconomyService economy;

    public ApplianceService(SaveRepository saves, EconomyService economy) {
        this.saves = saves;
        this.economy = economy;
    }

    public PurchaseOutcome buy(SaveState save, ApplianceItem item) {
        int price = economy.price(item.basePrice(), save);
        if (save.weekOver()) {
            return new PurchaseOutcome(PurchaseOutcome.Status.WEEK_OVER, save.timeMinutes(), save.cash(), price);
        }
        int cash = save.cash();
        if (cash < price) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, save.timeMinutes(), cash, price);
        }
        // Ownership is a repeat-purchase guard, not an affordability one — check it after cash so an
        // owner who's also broke still sees ALREADY_OWNED (the more specific, and correct, reason),
        // and before any charge so re-buying an owned item costs nothing and grants no state change.
        if (save.owns(item)) {
            return new PurchaseOutcome(PurchaseOutcome.Status.ALREADY_OWNED, save.timeMinutes(), cash, price);
        }
        save.setCash(cash - price);
        save.grantAppliance(item);
        save.addHappiness(item.firstOwnedHappiness());
        saves.update(save);
        return new PurchaseOutcome(PurchaseOutcome.Status.OK, save.timeMinutes(), save.cash(), price);
    }
}
