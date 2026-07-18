package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.model.ApplianceItem;
import amiss.domain.model.ClothingItem;
import amiss.domain.model.FastFoodItem;
import amiss.domain.model.FoodPack;
import amiss.domain.model.SaveState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private SaveRepository saves;

    private ShopService service() {
        return new ShopService(saves, ActionCosts.defaults(),
                new EconomyService(n -> 1)); // eatMinutes/shopMinutes = 0
    }

    // ---- eat -----------------------------------------------------------------

    @Test
    void eatingBuysTheMealAndSetsHappinessAndTheFastFoodFlag() {
        SaveState save = TestSaves.newSave(); // cash 100, eat 0, happiness 50

        EatOutcome result = service().eat(save, FastFoodItem.BURGER); // price 32

        assertEquals(EatOutcome.Status.OK, result.status());
        assertEquals(3600, result.remainingMinutes()); // eating is free by default
        assertEquals(68, result.cash());
        assertEquals(68, save.cash());
        assertEquals(0, save.eat());                   // fast food is its own track, never banked
        assertTrue(save.ateFastFoodLastTurn());
        assertEquals(51, save.happiness());
        verify(saves).update(save);
    }

    @Test
    void eatingWithFreshFoodAlreadyStoredLeavesTheStockUntouched() {
        SaveState save = TestSaves.newSave();
        save.setEat(3);

        service().eat(save, FastFoodItem.BURGER);

        assertEquals(3, save.eat()); // fast food never touches stored fresh food
    }

    @Test
    void eatingFastFoodSetsTheFlagButNeverTouchesStoredFreshFood() {
        SaveState save = TestSaves.newSave();
        save.setEat(3);

        EatOutcome outcome = service().eat(save, FastFoodItem.BURGER);

        assertEquals(EatOutcome.Status.OK, outcome.status());
        assertTrue(save.ateFastFoodLastTurn());
        assertEquals(3, save.eat());   // untouched — fast food is its own track
    }

    @Test
    void eatingChargesTimeBeforeCheckingCash() {
        SaveState save = TestSaves.newSave();
        save.setCash(10); // < 32

        EatOutcome result = service().eat(save, FastFoodItem.BURGER);

        assertEquals(EatOutcome.Status.INSUFFICIENT_CASH, result.status());
        assertEquals(10, result.cash());
        assertEquals(10, save.cash());
        assertEquals(0, save.eat()); // not fed
        verify(saves).update(save); // time was still "charged" (0 by default, but persisted)
    }

    @Test
    void eatingRejectsWhenConfiguredEatCostExceedsTheClock() {
        ActionCosts costed = new ActionCosts(360, 360, 360, 240, 120, 60, 0, 40, 120, 3600, 1200);
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(50); // 50 - 60 < 0

        EatOutcome result = new ShopService(saves, costed, new EconomyService(n -> 1)).eat(save, FastFoodItem.BURGER);

        assertEquals(EatOutcome.Status.INSUFFICIENT_TIME, result.status());
        assertEquals(50, result.remainingMinutes());
        assertEquals(-1, result.cash());
        assertEquals(50, save.timeMinutes());
        assertEquals(100, save.cash());
        verifyNoInteractions(saves);
    }

    // ---- buyGroceries ----------------------------------------------------------

    @Test
    void groceriesWithoutAFridgeResultInExactlyOneWeekWhenStockIsLow() {
        SaveState save = TestSaves.newSave(); // eat 0, cash 100

        PurchaseOutcome result = service().buyGroceries(save, FoodPack.ONE_WEEK); // price 25, weeks 1

        assertEquals(PurchaseOutcome.Status.OK, result.status());
        assertEquals(75, result.cash());
        assertEquals(1, save.eat());
        verify(saves).update(save);
    }

    @Test
    void groceriesWithoutAFridgeAlwaysResultInExactlyOneWeekRegardlessOfPackSize() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.setEat(0);

        service().buyGroceries(save, FoodPack.EIGHT_WEEKS);

        assertEquals(1, save.eat());
    }

    @Test
    void groceriesWithoutAFridgeDoNotStackOnExistingFridgelessFood() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.setEat(1);   // leftover from a prior fridgeless purchase

        service().buyGroceries(save, FoodPack.ONE_WEEK);

        assertEquals(1, save.eat());   // still exactly 1, not 2
    }

    @Test
    void groceriesWithoutAFridgeClampBackDownToOneWeekEvenWithLeftoverStock() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.setEat(3); // fridgeless food should never have reached 3, but prove the clamp anyway

        service().buyGroceries(save, FoodPack.ONE_WEEK);

        assertEquals(1, save.eat()); // fridgeless: always exactly 1, never additive
    }

    @Test
    void groceriesWithAFridgeBankUpToSixWeeks() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.grantAppliance(ApplianceItem.FRIDGE);
        save.setEat(2);

        service().buyGroceries(save, FoodPack.FOUR_WEEKS);

        assertEquals(6, save.eat());   // 2 + 4, under the 6-week cap
    }

    @Test
    void groceriesWithAFridgeClampAtSixWeeksWithoutAFreezer() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.grantAppliance(ApplianceItem.FRIDGE);
        save.setEat(5);

        service().buyGroceries(save, FoodPack.FOUR_WEEKS);

        assertEquals(6, save.eat());   // 5 + 4 = 9, clamped to 6
    }

    @Test
    void groceriesWithFridgeAndFreezerBankUpToTwelveWeeks() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.grantAppliance(ApplianceItem.FRIDGE);
        save.grantAppliance(ApplianceItem.FREEZER);
        save.setEat(10);

        service().buyGroceries(save, FoodPack.FOUR_WEEKS);

        assertEquals(12, save.eat());   // 10 + 4 = 14, clamped to 12
    }

    @Test
    void groceriesWithAFreezerButNoFridgeBehavesExactlyFridgeless() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.grantAppliance(ApplianceItem.FREEZER); // no Fridge

        service().buyGroceries(save, FoodPack.FOUR_WEEKS);

        assertEquals(1, save.eat());   // a Freezer alone does nothing; not 4, not 12
    }

    @Test
    void groceriesWithAFridgeProveAdditiveStackingNotJustClamping() {
        SaveState save = TestSaves.newSave();
        save.setCash(1000);
        save.grantAppliance(ApplianceItem.FRIDGE);
        save.setEat(1);

        service().buyGroceries(save, FoodPack.ONE_WEEK);

        assertEquals(2, save.eat());   // 1 + 1, well under the 6-week cap - proves it's not just "return cap"
    }

    @Test
    void groceriesInsufficientCashRejectsThePurchaseButHasAlreadyChargedTime() {
        SaveState save = TestSaves.newSave();
        save.setCash(10); // < 25

        PurchaseOutcome result = service().buyGroceries(save, FoodPack.ONE_WEEK);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_CASH, result.status());
        assertEquals(10, result.cash());
        assertEquals(10, save.cash());
        assertEquals(0, save.eat());
        verify(saves).update(save);
    }

    @Test
    void groceriesInsufficientTimeRejectsAndPersistsNothing() {
        ActionCosts costed = new ActionCosts(360, 360, 360, 240, 120, 60, 100, 40, 120, 3600, 1200);
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(50); // < 100

        PurchaseOutcome result = new ShopService(saves, costed, new EconomyService(n -> 1)).buyGroceries(save, FoodPack.ONE_WEEK);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_TIME, result.status());
        assertEquals(50, save.timeMinutes());
        assertEquals(100, save.cash());
        verifyNoInteractions(saves);
    }

    // ---- buyClothes (incl. the KAN-32 free-clothes-on-failed-purchase regression) ----

    @Test
    void buyingClothesRaisesTheLevelAndChargesCash() {
        SaveState save = TestSaves.newSave(); // casual 6, dress/business 0
        save.setCash(400); // enough for a Business Suit at 295

        PurchaseOutcome result = service().buyClothes(save, ClothingItem.BUSINESS); // price 295, level 3

        assertEquals(PurchaseOutcome.Status.OK, result.status());
        assertEquals(105, result.cash());
        assertEquals(13, save.businessWeeks());
        assertEquals(105, save.cash());
        verify(saves).update(save);
    }

    @Test
    void weekOverRejectsAndDoesNotGiveFreeClothes() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(0);

        PurchaseOutcome result = service().buyClothes(save, ClothingItem.BUSINESS);

        assertEquals(PurchaseOutcome.Status.WEEK_OVER, result.status());
        assertEquals(0, save.businessWeeks()); // unchanged
        assertEquals(100, save.cash());
        verifyNoInteractions(saves);
    }

    @Test
    void insufficientCashRejectsAndDoesNotGiveFreeClothes() {
        SaveState save = TestSaves.newSave();
        save.setCash(10); // < 295

        PurchaseOutcome result = service().buyClothes(save, ClothingItem.BUSINESS);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_CASH, result.status());
        assertEquals(10, result.cash());
        assertEquals(0, save.businessWeeks()); // unchanged - the KAN-32 fix
        verifyNoInteractions(saves);
    }

    @Test
    void insufficientTimeRejectsAndDoesNotGiveFreeClothes() {
        ActionCosts costed = new ActionCosts(360, 360, 360, 240, 120, 60, 100, 40, 120, 3600, 1200);
        SaveState save = TestSaves.newSave();
        save.setCash(400); // enough cash for the Business Suit so the time check is reached
        save.setTimeMinutes(50); // clock read (0-cost) OK, then 50 - 100 < 0

        PurchaseOutcome result = new ShopService(saves, costed, new EconomyService(n -> 1)).buyClothes(save, ClothingItem.BUSINESS);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_TIME, result.status());
        assertEquals(0, save.businessWeeks());
        assertEquals(400, save.cash());
        verifyNoInteractions(saves);
    }

    @Test
    void buyingClothesWithExactlyEnoughCashSucceeds() {
        SaveState save = TestSaves.newSave();
        save.setCash(295);

        PurchaseOutcome result = service().buyClothes(save, ClothingItem.BUSINESS);

        assertEquals(PurchaseOutcome.Status.OK, result.status());
        assertEquals(0, save.cash());
        assertEquals(13, save.businessWeeks());
    }

    // ---- economy pricing -------------------------------------------------------

    @Test
    void eatChargesTheEconomyAdjustedPrice() {
        SaveState save = TestSaves.newSave();
        save.setEconomyReading((short) 60);   // +100%: every price doubles
        ShopService shop = new ShopService(saves, ActionCosts.defaults(),
                new EconomyService(n -> 1));

        EatOutcome outcome = shop.eat(save, FastFoodItem.BURGER);

        assertEquals(EatOutcome.Status.OK, outcome.status());
        assertEquals(64, outcome.price());          // base 32 doubled
        assertEquals(100 - 64, save.cash());
    }

    @Test
    void groceriesAndClothesChargeAndReportTheAdjustedPrice() {
        SaveState save = TestSaves.newSave();
        save.setEconomyReading((short) -30);  // -50%: half price
        ShopService shop = new ShopService(saves, ActionCosts.defaults(),
                new EconomyService(n -> 1));

        PurchaseOutcome groceries = shop.buyGroceries(save, FoodPack.ONE_WEEK);
        assertEquals(12, groceries.pricePaid());    // floorDiv(25*-30,60)=-13 -> 12

        PurchaseOutcome clothes = shop.buyClothes(save, ClothingItem.CASUAL);
        assertEquals(36, clothes.pricePaid());      // base 73 halved: floorDiv(73*-30,60) = -37 -> 36
    }
}
