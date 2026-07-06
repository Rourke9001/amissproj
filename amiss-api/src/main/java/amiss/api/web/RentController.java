package amiss.api.web;

import amiss.api.config.GameServicesFactory;
import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InsufficientTimeException;
import amiss.api.error.RentNotDueException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.RentPaymentResponse;
import amiss.application.port.PersistenceFailureException;
import amiss.application.service.GameServices;
import amiss.application.service.RentPayment;
import amiss.application.service.RentService;
import amiss.domain.board.Location;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paying rent (KAN-31): only reachable while standing at the {@link Location#RENT_OFFICE}
 * stop. Every rejection status is a 409 (rent isn't due, or the game state blocks it) except
 * a persistence failure.
 */
@RestController
@RequestMapping("/api/players/{username}/rent")
public class RentController {

    private final GameServicesFactory factory;
    private final PlayerStateAssembler assembler;

    public RentController(GameServicesFactory factory, PlayerStateAssembler assembler) {
        this.factory = factory;
        this.assembler = assembler;
    }

    @PostMapping("/pay")
    public RentPaymentResponse pay(@PathVariable String username) {
        GameServices services = factory.forPlayer(username);
        LocationGuard.requireAt(services, Location.RENT_OFFICE);

        RentPayment payment = services.rent().payRent();
        switch (payment.status()) {
            case NOT_DUE:
                throw new RentNotDueException(username);
            case WEEK_OVER:
                throw new WeekOverException(username);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(username);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(username);
            case FAILED:
                throw new PersistenceFailureException("Rent payment failed for '" + username + "'");
            default:
                return new RentPaymentResponse(RentService.WEEKLY_RENT,
                        services.costs().payRentMinutes(), assembler.assemble(username, services));
        }
    }
}
