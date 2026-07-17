package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.model.ApplianceItem;
import amiss.domain.model.ClothingItem;
import amiss.domain.model.FastFoodItem;
import amiss.domain.model.FoodPack;
import amiss.domain.model.SaveState;

/**
 * Eating, buying groceries and buying clothes (KAN-53), the save-scoped port of the legacy
 * {@code StatsService}/{@code FoodService} shopping rules, driven by the {@link FastFoodItem},
 * {@link FoodPack} and {@link ClothingItem} catalogs.
 *
 * <p>Food is split into two independent tracks (KAN-23, wiki-exact). Fast food is never
 * banked: eating charges {@code eatMinutes} (0 by default) <em>before</em> checking cash — a
 * cash-short meal still burns the time, mirroring the Swing rule — then simply flags
 * {@code ateFastFoodLastTurn} on the save; it never reads or writes {@code eat}. Stored fresh
 * food ({@code eat}, in weeks) is fed only by groceries: without a Fridge, any purchase
 * feeds exactly one week and never stacks, regardless of pack size or existing stock — there
 * is nowhere to store the surplus. Owning a Fridge banks purchases additively up to a 6-week
 * cap; owning a Freezer too raises that cap to 12 weeks. Buying groceries charges
 * {@code shopMinutes} the same way as eating (time first, cash second). Buying clothes checks
 * the week, then cash, then time — and only then raises the clothing level — the KAN-32
 * ordering fix that stops a failed purchase from granting a free upgrade.
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

        save.setAteFastFoodLastTurn(true);
        save.setCash(cash - price);
        save.addHappiness(HAPPINESS_PER_MEAL);
        saves.update(save);
        return new EatOutcome(EatOutcome.Status.OK, remaining, save.cash(), price);
    }

    private static final int FRESH_FOOD_CAP_FRIDGE_ONLY = 6;
    private static final int FRESH_FOOD_CAP_WITH_FREEZER = 12;

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
        save.setEat(freshFoodWeeksAfterPurchase(save, pack));
        saves.update(save);
        return new PurchaseOutcome(PurchaseOutcome.Status.OK, remaining, save.cash(), price);
    }

    /**
     * Fridgeless: any purchase feeds exactly 1 week and never stacks (wiki-exact — the
     * pack's size is irrelevant with nowhere to store the surplus). With a Fridge: banks
     * up to 6 weeks, or 12 with a Freezer too.
     */
    private static int freshFoodWeeksAfterPurchase(SaveState save, FoodPack pack) {
        if (!save.owns(ApplianceItem.FRIDGE)) {
            return 1;
        }
        int cap = save.owns(ApplianceItem.FREEZER) ? FRESH_FOOD_CAP_WITH_FREEZER : FRESH_FOOD_CAP_FRIDGE_ONLY;
        return Math.min(cap, save.eat() + pack.weeks());
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
}
