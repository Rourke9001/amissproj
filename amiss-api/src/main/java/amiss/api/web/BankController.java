package amiss.api.web;

import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InvalidAmountException;
import amiss.api.web.dto.BankRequest;
import amiss.api.web.dto.BankTransactionResponse;
import amiss.application.service.save.BankTransaction;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bank deposits and withdrawals (KAN-54: cut over to {@code /api/saves/{saveId}/bank}).
 * Transactions cost no time (the building-entry cost is already charged by {@code move}) and
 * are only reachable while standing at the {@link Location#BANK} stop.
 */
@RestController
@RequestMapping("/api/saves/{saveId}/bank")
public class BankController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;

    public BankController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
    }

    @PostMapping("/deposit")
    public BankTransactionResponse deposit(@PathVariable long saveId, Authentication authentication,
            @RequestBody BankRequest request) {
        return transact(saveId, authentication, request, true, "deposit");
    }

    @PostMapping("/withdraw")
    public BankTransactionResponse withdraw(@PathVariable long saveId, Authentication authentication,
            @RequestBody BankRequest request) {
        return transact(saveId, authentication, request, false, "withdraw");
    }

    private BankTransactionResponse transact(long saveId, Authentication authentication, BankRequest request,
            boolean deposit, String operation) {
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.BANK);

        int amount = request.amount() == null ? 0 : request.amount();
        BankTransaction result = deposit ? services.bank().deposit(save, amount) : services.bank().withdraw(save, amount);
        switch (result.status()) {
            case INVALID_AMOUNT:
                throw new InvalidAmountException(amount);
            case INSUFFICIENT_FUNDS:
                throw new InsufficientFundsException(saveId);
            default:
                return new BankTransactionResponse(operation, amount, assembler.assemble(services, save));
        }
    }
}
