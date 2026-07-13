package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
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
    void eatingBuysTheMealAndToPsFoodAndHappiness() {
        SaveState save = TestSaves.newSave(); // cash 100, eat 0, happiness 50

        EatOutcome result = service().eat(save, FastFoodItem.BURGER); // price 32

        assertEquals(EatOutcome.Status.OK, result.status());
        assertEquals(4320, result.remainingMinutes()); // eating is free by default
        assertEquals(68, result.cash());
        assertEquals(68, save.cash());
        assertEquals(1, save.eat());                   // had none -> topped to 1
        assertEquals(51, save.happiness());
        verify(saves).update(save);
    }

    @Test
    void eatingWithFoodAlreadyStoredLeavesTheStockUntouched() {
        SaveState save = TestSaves.newSave();
        save.setEat(3);

        service().eat(save, FastFoodItem.BURGER);

        assertEquals(3, save.eat()); // legacy setFood(0) branch: unchanged, not decremented
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
        ActionCosts costed = new ActionCosts(360, 360, 360, 240, 120, 60, 0, 40, 120, 3600, 4320);
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
    void groceriesToTheStockToExactlyOneWeekWhenStockIsLow() {
        SaveState save = TestSaves.newSave(); // eat 0, cash 100

        PurchaseOutcome result = service().buyGroceries(save, FoodPack.ONE_WEEK); // price 25, weeks 1

        assertEquals(PurchaseOutcome.Status.OK, result.status());
        assertEquals(75, result.cash());
        assertEquals(1, save.eat());
        verify(saves).update(save);
    }

    @Test
    void aOneWeekPackAddsInsteadOfToppingWhenStockIsAlreadyTwoOrMore() {
        SaveState save = TestSaves.newSave();
        save.setEat(3);

        service().buyGroceries(save, FoodPack.ONE_WEEK);

        assertEquals(4, save.eat()); // 3 + 1, not topped
    }

    @Test
    void aMultiWeekPackAlwaysAddsOutright() {
        SaveState save = TestSaves.newSave();
        save.setEat(0);

        service().buyGroceries(save, FoodPack.FOUR_WEEKS); // weeks 4

        assertEquals(4, save.eat());
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
        ActionCosts costed = new ActionCosts(360, 360, 360, 240, 120, 60, 100, 40, 120, 3600, 4320);
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
        SaveState save = TestSaves.newSave(); // clothing 1, cash 100

        PurchaseOutcome result = service().buyClothes(save, ClothingItem.SUIT); // price 55, level 3

        assertEquals(PurchaseOutcome.Status.OK, result.status());
        assertEquals(45, result.cash());
        assertEquals(3, save.clothing());
        assertEquals(45, save.cash());
        verify(saves).update(save);
    }

    @Test
    void weekOverRejectsAndDoesNotGiveFreeClothes() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(0);

        PurchaseOutcome result = service().buyClothes(save, ClothingItem.SUIT);

        assertEquals(PurchaseOutcome.Status.WEEK_OVER, result.status());
        assertEquals(1, save.clothing()); // unchanged
        assertEquals(100, save.cash());
        verifyNoInteractions(saves);
    }

    @Test
    void insufficientCashRejectsAndDoesNotGiveFreeClothes() {
        SaveState save = TestSaves.newSave();
        save.setCash(10); // < 55

        PurchaseOutcome result = service().buyClothes(save, ClothingItem.SUIT);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_CASH, result.status());
        assertEquals(10, result.cash());
        assertEquals(1, save.clothing()); // unchanged - the KAN-32 fix
        verifyNoInteractions(saves);
    }

    @Test
    void insufficientTimeRejectsAndDoesNotGiveFreeClothes() {
        ActionCosts costed = new ActionCosts(360, 360, 360, 240, 120, 60, 100, 40, 120, 3600, 4320);
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(50); // clock read (0-cost) OK, then 50 - 100 < 0

        PurchaseOutcome result = new ShopService(saves, costed, new EconomyService(n -> 1)).buyClothes(save, ClothingItem.SUIT);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_TIME, result.status());
        assertEquals(1, save.clothing());
        assertEquals(100, save.cash());
        verifyNoInteractions(saves);
    }

    @Test
    void buyingClothesWithExactlyEnoughCashSucceeds() {
        SaveState save = TestSaves.newSave();
        save.setCash(55);

        PurchaseOutcome result = service().buyClothes(save, ClothingItem.SUIT);

        assertEquals(PurchaseOutcome.Status.OK, result.status());
        assertEquals(0, save.cash());
        assertEquals(3, save.clothing());
    }

    // ---- economy pricing -------------------------------------------------------

    @Test
    void eatChargesTheEconomyAdjustedPrice() {
        SaveState save = TestSaves.newSave();
        save.setEconomyReading(60);   // +100%: every price doubles
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
        save.setEconomyReading(-30);  // -50%: half price
        ShopService shop = new ShopService(saves, ActionCosts.defaults(),
                new EconomyService(n -> 1));

        PurchaseOutcome groceries = shop.buyGroceries(save, FoodPack.ONE_WEEK);
        assertEquals(12, groceries.pricePaid());    // floorDiv(25*-30,60)=-13 -> 12

        PurchaseOutcome clothes = shop.buyClothes(save, ClothingItem.CASUAL);
        assertEquals(10, clothes.pricePaid());      // base 20 halved
    }
}
