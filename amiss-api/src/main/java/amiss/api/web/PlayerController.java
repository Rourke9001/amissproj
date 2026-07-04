package amiss.api.web;

import amiss.api.config.GameServicesFactory;
import amiss.api.error.WeekNotOverException;
import amiss.api.web.dto.EndWeekResponse;
import amiss.api.web.dto.PlayerStateDto;
import amiss.application.service.GameServices;
import amiss.application.service.WeekSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The player's turn lifecycle (KAN-29): read state, and explicitly end a used-up week —
 * there is no silent rollover, so {@code end-week} is the only way into the next round and
 * conflicts (409) while time remains.
 */
@RestController
@RequestMapping("/api/players/{username}")
public class PlayerController {

    private final GameServicesFactory factory;
    private final PlayerStateAssembler assembler;

    public PlayerController(GameServicesFactory factory, PlayerStateAssembler assembler) {
        this.factory = factory;
        this.assembler = assembler;
    }

    @GetMapping
    public PlayerStateDto state(@PathVariable String username) {
        return assembler.assemble(username, factory.forPlayer(username));
    }

    @PostMapping("/end-week")
    public EndWeekResponse endWeek(@PathVariable String username) {
        GameServices services = factory.forPlayer(username);
        WeekSummary summary = services.turn().endWeek();
        if (!summary.weekEnded()) {
            throw new WeekNotOverException(username);
        }
        return new EndWeekResponse(summary.round(), summary.fed(), summary.rentDue(),
                summary.debtCharged(), assembler.assemble(username, services));
    }
}
