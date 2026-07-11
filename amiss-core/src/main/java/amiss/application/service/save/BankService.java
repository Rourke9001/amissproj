package amiss.application.service.save;

import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;

/**
 * The player's bank savings (KAN-53), the save-scoped port of the legacy {@code BankService}.
 * Deposits and withdrawals cost no time (the building-entry cost is already charged by
 * {@link TravelService}); there is no interest (KAN-5 economy epic).
 */
public class BankService {

    private final SaveRepository saves;

    public BankService(SaveRepository saves) {
        this.saves = saves;
    }

    /** Moves {@code amount} from cash to bank. */
    public BankTransaction deposit(SaveState save, int amount) {
        return transfer(save, amount, true);
    }

    /** Moves {@code amount} from bank to cash. */
    public BankTransaction withdraw(SaveState save, int amount) {
        return transfer(save, amount, false);
    }

    private BankTransaction transfer(SaveState save, int amount, boolean deposit) {
        if (amount <= 0) {
            return new BankTransaction(BankTransaction.Status.INVALID_AMOUNT, -1, -1);
        }
        int source = deposit ? save.cash() : save.bank();
        if (amount > source) {
            return new BankTransaction(BankTransaction.Status.INSUFFICIENT_FUNDS, save.cash(), save.bank());
        }
        if (deposit) {
            save.setCash(save.cash() - amount);
            save.setBank(save.bank() + amount);
        } else {
            save.setBank(save.bank() - amount);
            save.setCash(save.cash() + amount);
        }
        saves.update(save);
        return new BankTransaction(BankTransaction.Status.OK, save.cash(), save.bank());
    }
}
