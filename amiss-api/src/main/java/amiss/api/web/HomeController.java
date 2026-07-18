package amiss.api.web;

import amiss.api.error.InsufficientTimeException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.RelaxResponse;
import amiss.application.config.ActionCosts;
import amiss.application.service.save.RelaxService;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Relax action at Home (KAN-23) — 6h for +3 Relaxation, +2 happiness on the first
 *  Relax of a turn only. */
@RestController
public class HomeController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;
    private final ActionCosts costs;

    public HomeController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler,
            ActionCosts costs) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
        this.costs = costs;
    }

    @PostMapping("/api/saves/{saveId}/relax")
    public RelaxResponse relax(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.LOW_COST_HOUSING);

        RelaxService.RelaxOutcome outcome = services.relax().relax(save);
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            default:
                return new RelaxResponse(costs.relaxMinutes(), outcome.relaxation(),
                        assembler.assemble(services, save));
        }
    }
}
