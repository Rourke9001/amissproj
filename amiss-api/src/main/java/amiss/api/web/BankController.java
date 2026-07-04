package amiss.api.web;

import amiss.api.config.GameServicesFactory;
import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InvalidAmountException;
import amiss.api.error.PersistenceFailureException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.BankRequest;
import amiss.api.web.dto.BankTransactionResponse;
import amiss.application.service.BankTransaction;
import amiss.application.service.GameServices;
import amiss.domain.board.Location;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bank deposits and withdrawals (KAN-31, greenfield). Transactions cost no time (the 2h
 * building-entry cost is already charged by {@code move}) and are only reachable while
 * standing at the {@link Location#BANK} stop.
 */
@RestController
@RequestMapping("/api/players/{username}/bank")
public class BankController {

    private final GameServicesFactory factory;
    private final PlayerStateAssembler assembler;

    public BankController(GameServicesFactory factory, PlayerStateAssembler assembler) {
        this.factory = factory;
        this.assembler = assembler;
    }

    @PostMapping("/deposit")
    public BankTransactionResponse deposit(@PathVariable String username, @RequestBody BankRequest request) {
        return transact(username, request, true, "deposit");
    }

    @PostMapping("/withdraw")
    public BankTransactionResponse withdraw(@PathVariable String username, @RequestBody BankRequest request) {
        return transact(username, request, false, "withdraw");
    }

    private BankTransactionResponse transact(String username, BankRequest request, boolean deposit, String operation) {
        GameServices services = factory.forPlayer(username);
        if (services.time().spendMinutes(0).weekOver()) {
            throw new WeekOverException(username);
        }
        LocationGuard.requireAt(services, Location.BANK);

        int amount = request.amount() == null ? 0 : request.amount();
        BankTransaction result = deposit ? services.bank().deposit(amount) : services.bank().withdraw(amount);
        switch (result.status()) {
            case INVALID_AMOUNT:
                throw new InvalidAmountException(amount);
            case INSUFFICIENT_FUNDS:
                throw new InsufficientFundsException(username);
            case FAILED:
                throw new PersistenceFailureException("Bank transfer failed for '" + username + "'");
            default:
                return new BankTransactionResponse(operation, amount, assembler.assemble(username, services));
        }
    }
}
