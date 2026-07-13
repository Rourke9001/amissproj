package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.model.ClothingItem;
import amiss.domain.model.FastFoodItem;
import amiss.domain.model.FoodPack;
import amiss.domain.model.SaveState;

/**
 * Eating, buying groceries and buying clothes (KAN-53), the save-scoped port of the legacy
 * {@code StatsService}/{@code FoodService} shopping rules, driven by the {@link FastFoodItem},
 * {@link FoodPack} and {@link ClothingItem} catalogs.
 *
 * <p>Eating charges {@code eatMinutes} (0 by default) <em>before</em> checking cash — a
 * cash-short meal still burns the time, mirroring the Swing rule — then tops the stored food
 * to at least one week ({@link #addFood}); a stock of two or more weeks is left untouched.
 * Buying groceries charges {@code shopMinutes} the same way (time first, cash second) and
 * folds the pack's weeks onto the stored food per the legacy {@code FoodService.setFood}
 * branch: a one-week pack tops the stock to exactly one when it is 0 or 1, but every pack
 * (including the one-week pack against a stock of 2+) otherwise adds its weeks outright.
 * Buying clothes checks the week, then cash, then time — and only then raises the clothing
 * level — the KAN-32 ordering fix that stops a failed purchase from granting a free upgrade.
 */
public class ShopService {

    private static final int HAPPINESS_PER_MEAL = 1;

    private final SaveRepository saves;
    private final ActionCosts costs;
    private final EconomyService economy;

    public ShopService(SaveRepository saves, ActionCosts costs, EconomyService economy) {
        this.saves = saves;
        this.costs = costs;
        this.economy = economy;
    }

    /** Buys and eats {@code item} at Monolith Burgers. */
    public EatOutcome eat(SaveState save, FastFoodItem item) {
        int price = economy.price(item.price(), save);
        int time = save.timeMinutes();
        int remaining = time - costs.eatMinutes();
        if (remaining < 0) {
            return new EatOutcome(EatOutcome.Status.INSUFFICIENT_TIME, time, -1, price);
        }
        save.setTimeMinutes(remaining);

        int cash = save.cash();
        if (cash < price) {
            saves.update(save);
            return new EatOutcome(EatOutcome.Status.INSUFFICIENT_CASH, remaining, cash, price);
        }

        save.setEat(addFood(save.eat(), save.eat() >= 1 ? 0 : 1));
        save.setCash(cash - price);
        save.addHappiness(HAPPINESS_PER_MEAL);
        saves.update(save);
        return new EatOutcome(EatOutcome.Status.OK, remaining, save.cash(), price);
    }

    /** Buys {@code pack} at Black's Market. */
    public PurchaseOutcome buyGroceries(SaveState save, FoodPack pack) {
        int price = economy.price(pack.price(), save);
        int time = save.timeMinutes();
        int remaining = time - costs.shopMinutes();
        if (remaining < 0) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_TIME, time, save.cash(), price);
        }
        save.setTimeMinutes(remaining);

        int cash = save.cash();
        if (cash < price) {
            saves.update(save);
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, remaining, cash, price);
        }

        save.setCash(cash - price);
        save.setEat(addFood(save.eat(), pack.weeks()));
        saves.update(save);
        return new PurchaseOutcome(PurchaseOutcome.Status.OK, remaining, save.cash(), price);
    }

    /** Buys {@code item} at QT Clothing. */
    public PurchaseOutcome buyClothes(SaveState save, ClothingItem item) {
        int price = economy.price(item.price(), save);
        if (save.weekOver()) {
            return new PurchaseOutcome(PurchaseOutcome.Status.WEEK_OVER, save.timeMinutes(), save.cash(), price);
        }

        int cash = save.cash();
        if (cash < price) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, save.timeMinutes(), cash, price);
        }

        int time = save.timeMinutes();
        int remaining = time - costs.shopMinutes();
        if (remaining < 0) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_TIME, time, cash, price);
        }

        save.setTimeMinutes(remaining);
        save.setCash(cash - price);
        save.setClothing(item.level());
        saves.update(save);
        return new PurchaseOutcome(PurchaseOutcome.Status.OK, remaining, save.cash(), price);
    }

    /**
     * Mirrors the legacy {@code FoodService.setFood} branch exactly: a one-week top-up
     * ({@code count == 1}) snaps the stock to exactly one when it was 0 or 1; every other case
     * (including a one-week top-up against 2+ weeks already stored) adds {@code count} outright.
     */
    private static int addFood(int current, int count) {
        if ((current == 0 || current == 1) && count == 1) {
            return 1;
        }
        return current + count;
    }
}
