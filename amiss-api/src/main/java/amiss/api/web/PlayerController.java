package amiss.api.web;

import amiss.api.error.InsufficientTimeException;
import amiss.api.error.UnknownLocationException;
import amiss.api.error.WeekNotOverException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.EconomyEventDto;
import amiss.api.web.dto.EndWeekResponse;
import amiss.api.web.dto.MoveRequest;
import amiss.api.web.dto.MoveResponse;
import amiss.api.web.dto.SaveStateDto;
import amiss.application.service.save.MoveResult;
import amiss.application.service.save.SaveGameServices;
import amiss.application.service.save.WeekRolloverService;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The save's turn lifecycle (KAN-54: cut over from {@code /api/players/{username}} to
 * {@code /api/saves/{saveId}}): read state, move, and explicitly end a used-up week — there
 * is no silent rollover, so {@code end-week} is the only way into the next round and
 * conflicts (409) while time remains.
 */
@RestController
@RequestMapping("/api/saves/{saveId}")
public class PlayerController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;

    public PlayerController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
    }

    @GetMapping
    public SaveStateDto state(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        return assembler.assemble(services, save);
    }

    @PostMapping("/end-week")
    public EndWeekResponse endWeek(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        WeekRolloverService.RolloverResult result = services.weeks().endWeek(save);
        if (!result.rolled()) {
            throw new WeekNotOverException(saveId);
        }
        return new EndWeekResponse(result.newRound(), result.fed(), result.rentDue(),
                result.debtCharged(), result.won(), EconomyEventDto.from(result.economy()),
                assembler.assemble(services, save));
    }

    @PostMapping("/move")
    public MoveResponse move(@PathVariable long saveId, Authentication authentication, @RequestBody MoveRequest request) {
        Location target = parseTarget(request.target());
        SaveState save = scope.require(saveId, authentication);
        MoveResult result = services.travel().moveTo(save, target);
        if (result.status() == MoveResult.Status.WEEK_OVER) {
            throw new WeekOverException(saveId);
        }
        if (result.status() == MoveResult.Status.INSUFFICIENT_TIME) {
            throw new InsufficientTimeException(saveId);
        }
        return new MoveResponse(target.name(), result.steps(), result.minutesCharged(),
                assembler.assemble(services, save));
    }

    private static Location parseTarget(String raw) {
        if (raw == null) {
            throw new UnknownLocationException(raw);
        }
        try {
            return Location.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownLocationException(raw);
        }
    }
}
