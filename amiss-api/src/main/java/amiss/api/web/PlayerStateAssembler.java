package amiss.api.web;

import amiss.api.web.dto.PlayerStateDto;
import amiss.application.service.GameServices;
import amiss.application.service.StatsService;
import amiss.application.service.TimeService;
import amiss.application.service.TimeSpend;
import amiss.domain.board.Board;
import amiss.domain.board.Location;
import org.springframework.stereotype.Component;

/**
 * Builds the wire representation of a player from their {@link GameServices}. Board data
 * comes exclusively from {@code domain.board.Board} — never from the Swing-side
 * {@code OpenLocation}, whose coordinate map is stale. A saved position that is not a stop
 * (e.g. from a pre-ring save) is reported as home; gameplay actions persist real positions.
 */
@Component
public class PlayerStateAssembler {

    private final Board board = new Board();

    public PlayerStateDto assemble(String username, GameServices services) {
        TimeService time = services.time();
        StatsService stats = services.stats();

        TimeSpend clock = time.spendMinutes(0);
        int row = time.getX();
        int col = time.getY();
        if (!board.isStop(row, col)) {
            int[] home = board.cellOf(0);
            row = home[0];
            col = home[1];
        }
        Location location = board.locationAt(row, col);

        return new PlayerStateDto(
                username,
                Integer.parseInt(time.getRound()),
                clock.remainingMinutes(),
                TimeService.format(clock.remainingMinutes()),
                clock.weekOver(),
                stats.getCash(),
                stats.getDebt(),
                stats.getRent() == 1,
                new PlayerStateDto.LocationDto(location.name(), location.displayName(),
                        board.ringIndex(row, col), row, col));
    }
}
