package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RentServiceTest {

    @Mock
    private SaveRepository saves;

    private RentService service() {
        return new RentService(saves, ActionCosts.defaults());
    }

    /** Rent due: a 4th round with the rent flag still set. */
    private SaveState dueSave() {
        SaveState save = TestSaves.newSave(); // round 1, rent 1, cash 100, time 4320
        save.setRound(4);
        return save;
    }

    @Test
    void notDueWhenNotAFourthRound() {
        SaveState save = TestSaves.newSave();
        save.setRound(5); // 5 % 4 != 0, rent flag still 1

        RentPayment result = service().payRent(save);

        assertEquals(RentPayment.Status.NOT_DUE, result.status());
        assertEquals(4320, result.remainingMinutes());
        assertEquals(100, result.cash());
        verifyNoInteractions(saves);
    }

    @Test
    void notDueWhenAlreadyPaidThisRound() {
        SaveState save = TestSaves.newSave();
        save.setRound(4);
        save.setRent(0); // already paid

        RentPayment result = service().payRent(save);

        assertEquals(RentPayment.Status.NOT_DUE, result.status());
        verifyNoInteractions(saves);
    }

    @Test
    void weekOverRefusesEvenWhenDue() {
        SaveState save = dueSave();
        save.setTimeMinutes(0);

        RentPayment result = service().payRent(save);

        assertEquals(RentPayment.Status.WEEK_OVER, result.status());
        assertEquals(0, result.remainingMinutes());
        assertEquals(100, result.cash());
        verifyNoInteractions(saves);
    }

    @Test
    void insufficientCashRefusesAndChargesNothing() {
        SaveState save = dueSave();
        save.setCash(50); // < 80

        RentPayment result = service().payRent(save);

        assertEquals(RentPayment.Status.INSUFFICIENT_CASH, result.status());
        assertEquals(4320, result.remainingMinutes());
        assertEquals(50, result.cash());
        assertEquals(1, save.rent());
        verifyNoInteractions(saves);
    }

    @Test
    void insufficientTimeRefusesAndChargesNothing() {
        SaveState save = dueSave();
        save.setTimeMinutes(50); // < 120 required, but > 0

        RentPayment result = service().payRent(save);

        assertEquals(RentPayment.Status.INSUFFICIENT_TIME, result.status());
        assertEquals(50, result.remainingMinutes());
        assertEquals(100, result.cash());
        assertEquals(50, save.timeMinutes());
        assertEquals(1, save.rent());
        verifyNoInteractions(saves);
    }

    @Test
    void payingClearsRentAndChargesCashAndTime() {
        SaveState save = dueSave();

        RentPayment result = service().payRent(save);

        assertEquals(RentPayment.Status.OK, result.status());
        assertEquals(4200, result.remainingMinutes()); // 4320 - 120
        assertEquals(20, result.cash());                // 100 - 80
        assertEquals(0, save.rent());
        assertEquals(20, save.cash());
        assertEquals(4200, save.timeMinutes());
        verify(saves).update(save);
    }

    @Test
    void payingWithExactlyEnoughCashSucceeds() {
        SaveState save = dueSave();
        save.setCash(80);

        RentPayment result = service().payRent(save);

        assertEquals(RentPayment.Status.OK, result.status());
        assertEquals(0, save.cash());
        verify(saves).update(save);
    }
}
