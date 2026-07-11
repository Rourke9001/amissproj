package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankServiceTest {

    @Mock
    private SaveRepository saves;

    private BankService service() {
        return new BankService(saves);
    }

    @Test
    void depositMovesCashToBank() {
        SaveState save = TestSaves.newSave(); // cash 100, bank 0

        BankTransaction result = service().deposit(save, 40);

        assertEquals(BankTransaction.Status.OK, result.status());
        assertEquals(60, result.cash());
        assertEquals(40, result.bank());
        assertEquals(60, save.cash());
        assertEquals(40, save.bank());
        verify(saves).update(save);
    }

    @Test
    void depositOfExactlyAllCashSucceeds() {
        SaveState save = TestSaves.newSave(); // cash 100

        BankTransaction result = service().deposit(save, 100);

        assertEquals(BankTransaction.Status.OK, result.status());
        assertEquals(0, save.cash());
        assertEquals(100, save.bank());
        verify(saves).update(save);
    }

    @Test
    void depositAboveCashIsRejectedAndPersistsNothing() {
        SaveState save = TestSaves.newSave(); // cash 100

        BankTransaction result = service().deposit(save, 101);

        assertEquals(BankTransaction.Status.INSUFFICIENT_FUNDS, result.status());
        assertEquals(100, result.cash());
        assertEquals(0, result.bank());
        assertEquals(100, save.cash());
        assertEquals(0, save.bank());
        verifyNoInteractions(saves);
    }

    @Test
    void depositOfZeroOrNegativeIsInvalid() {
        SaveState save = TestSaves.newSave();

        BankTransaction zero = service().deposit(save, 0);
        BankTransaction negative = service().deposit(save, -5);

        assertEquals(BankTransaction.Status.INVALID_AMOUNT, zero.status());
        assertEquals(-1, zero.cash());
        assertEquals(-1, zero.bank());
        assertEquals(BankTransaction.Status.INVALID_AMOUNT, negative.status());
        verifyNoInteractions(saves);
    }

    @Test
    void withdrawMovesBankToCash() {
        SaveState save = TestSaves.newSave();
        save.setBank(50);

        BankTransaction result = service().withdraw(save, 20);

        assertEquals(BankTransaction.Status.OK, result.status());
        assertEquals(120, result.cash());
        assertEquals(30, result.bank());
        assertEquals(120, save.cash());
        assertEquals(30, save.bank());
        verify(saves).update(save);
    }

    @Test
    void withdrawOfExactlyAllBankSucceeds() {
        SaveState save = TestSaves.newSave();
        save.setBank(50);

        BankTransaction result = service().withdraw(save, 50);

        assertEquals(BankTransaction.Status.OK, result.status());
        assertEquals(0, save.bank());
        assertEquals(150, save.cash());
        verify(saves).update(save);
    }

    @Test
    void withdrawAboveBankIsRejectedAndPersistsNothing() {
        SaveState save = TestSaves.newSave();
        save.setBank(50);

        BankTransaction result = service().withdraw(save, 51);

        assertEquals(BankTransaction.Status.INSUFFICIENT_FUNDS, result.status());
        assertEquals(100, result.cash());
        assertEquals(50, result.bank());
        assertEquals(100, save.cash());
        assertEquals(50, save.bank());
        verifyNoInteractions(saves);
    }

    @Test
    void withdrawOfZeroOrNegativeIsInvalid() {
        SaveState save = TestSaves.newSave();

        BankTransaction result = service().withdraw(save, 0);

        assertEquals(BankTransaction.Status.INVALID_AMOUNT, result.status());
        assertEquals(-1, result.cash());
        assertEquals(-1, result.bank());
        verifyNoInteractions(saves);
    }

    @Test
    void depositRefusesOnceTheWeekIsOver() {
        SaveState save = TestSaves.newSave(); // cash 100, bank 0
        save.setTimeMinutes(0);

        BankTransaction result = service().deposit(save, 40);

        assertEquals(BankTransaction.Status.WEEK_OVER, result.status());
        assertEquals(100, result.cash());
        assertEquals(0, result.bank());
        assertEquals(100, save.cash());
        assertEquals(0, save.bank());
        verifyNoInteractions(saves);
    }

    @Test
    void withdrawRefusesOnceTheWeekIsOver() {
        SaveState save = TestSaves.newSave();
        save.setBank(50);
        save.setTimeMinutes(0);

        BankTransaction result = service().withdraw(save, 20);

        assertEquals(BankTransaction.Status.WEEK_OVER, result.status());
        assertEquals(100, result.cash());
        assertEquals(50, result.bank());
        assertEquals(100, save.cash());
        assertEquals(50, save.bank());
        verifyNoInteractions(saves);
    }
}
