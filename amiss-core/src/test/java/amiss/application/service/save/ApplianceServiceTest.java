package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import amiss.application.port.SaveRepository;
import amiss.domain.model.ApplianceItem;
import amiss.domain.model.SaveState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplianceServiceTest {

    @Mock
    private SaveRepository saves;

    private ApplianceService service() {
        return new ApplianceService(saves, new EconomyService(n -> 1));
    }

    @Test
    void firstFridgePurchaseGrantsOwnershipAndHappiness() {
        SaveState save = TestSaves.newSave();   // cash 100 by default — raise it, Fridge costs 876
        save.setCash(1000);

        PurchaseOutcome outcome = service().buy(save, ApplianceItem.FRIDGE);

        assertEquals(PurchaseOutcome.Status.OK, outcome.status());
        assertEquals(876, outcome.pricePaid());
        assertEquals(1000 - 876, outcome.cash());
        assertTrue(save.owns(ApplianceItem.FRIDGE));
        assertEquals(51, save.happiness());              // +1 first-owned bonus
        verify(saves).update(save);
    }

    @Test
    void repeatPurchaseOfAnOwnedApplianceGrantsNoFurtherHappiness() {
        SaveState save = TestSaves.newSave();
        save.setCash(2000);
        save.grantAppliance(ApplianceItem.FRIDGE);
        int happinessBefore = save.happiness();

        service().buy(save, ApplianceItem.FRIDGE);

        assertEquals(happinessBefore, save.happiness());
    }

    @Test
    void insufficientCashRejectsWithoutGrantingOwnership() {
        SaveState save = TestSaves.newSave();
        save.setCash(10);

        PurchaseOutcome outcome = service().buy(save, ApplianceItem.FRIDGE);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_CASH, outcome.status());
        assertEquals(10, save.cash());
        assertTrue(save.ownedAppliances().isEmpty());
    }

    @Test
    void weekOverRejectsThePurchase() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(0);

        PurchaseOutcome outcome = service().buy(save, ApplianceItem.FREEZER);

        assertEquals(PurchaseOutcome.Status.WEEK_OVER, outcome.status());
        assertTrue(save.ownedAppliances().isEmpty());
    }
}
