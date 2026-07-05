package amiss.application.service;

import amiss.application.port.PersistenceFailureException;
import amiss.application.port.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The player's bank savings (KAN-31, greenfield — there was no bank column before this).
 * Deposits and withdrawals cost no time (the 2h building-entry cost is already charged by
 * {@link TravelService}); there is no interest (KAN-5 economy epic). Swing does not use this
 * service: the bank is API-only for now.
 */
public class BankService {

    private static final Logger log = LoggerFactory.getLogger(BankService.class);

    private final UserRepository users;
    private final String username;

    public BankService(UserRepository users, String username) {
        this.users = users;
        this.username = username;
    }

    /** The player's savings balance, or -1 on a persistence failure. */
    public int balance() {
        try {
            return users.getBank(username);
        } catch (PersistenceFailureException ex) {
            log.warn("Failed to get bank balance", ex);
            return -1;
        }
    }

    /** Moves {@code amount} from cash to bank. */
    public BankTransaction deposit(int amount) {
        return transfer(amount, true);
    }

    /** Moves {@code amount} from bank to cash. */
    public BankTransaction withdraw(int amount) {
        return transfer(amount, false);
    }

    private BankTransaction transfer(int amount, boolean deposit) {
        if (amount <= 0) {
            return new BankTransaction(BankTransaction.Status.INVALID_AMOUNT, -1, -1);
        }
        try {
            boolean ok = deposit ? users.depositToBank(username, amount) : users.withdrawFromBank(username, amount);
            if (!ok) {
                return new BankTransaction(BankTransaction.Status.INSUFFICIENT_FUNDS,
                        users.getCash(username), users.getBank(username));
            }
            return new BankTransaction(BankTransaction.Status.OK,
                    users.getCash(username), users.getBank(username));
        } catch (PersistenceFailureException ex) {
            log.warn("Failed to transfer bank funds", ex);
            return new BankTransaction(BankTransaction.Status.FAILED, -1, -1);
        }
    }
}
