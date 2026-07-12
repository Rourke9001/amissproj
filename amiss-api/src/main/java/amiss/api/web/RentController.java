package amiss.api.web;

import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InsufficientTimeException;
import amiss.api.error.RentNotDueException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.RentPaymentResponse;
import amiss.application.config.ActionCosts;
import amiss.application.service.save.RentPayment;
import amiss.application.service.save.RentService;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paying rent (KAN-54: cut over to {@code /api/saves/{saveId}/rent}): only reachable while
 * standing at the {@link Location#RENT_OFFICE} stop. Every rejection status is a 409.
 */
@RestController
@RequestMapping("/api/saves/{saveId}/rent")
public class RentController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;
    private final ActionCosts costs;

    public RentController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler,
            ActionCosts costs) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
        this.costs = costs;
    }

    @PostMapping("/pay")
    public RentPaymentResponse pay(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.RENT_OFFICE);

        RentPayment payment = services.rent().payRent(save);
        switch (payment.status()) {
            case NOT_DUE:
                throw new RentNotDueException(saveId);
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(saveId);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            default:
                return new RentPaymentResponse(RentService.WEEKLY_RENT, costs.payRentMinutes(),
                        assembler.assemble(services, save));
        }
    }
}
