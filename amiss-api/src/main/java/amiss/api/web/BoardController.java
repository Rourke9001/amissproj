package amiss.api.web;

import amiss.api.web.dto.BoardDto;
import amiss.api.web.dto.LocationDto;
import amiss.application.config.ActionCosts;
import amiss.domain.board.Board;
import amiss.domain.board.Location;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The static board model (KAN-30): the 13-stop ring and the travel price metadata, straight
 * from {@code domain.board.Board} and the configured {@link ActionCosts}.
 */
@RestController
public class BoardController {

    private final ActionCosts costs;
    private final Board board = new Board();

    public BoardController(ActionCosts costs) {
        this.costs = costs;
    }

    @GetMapping("/api/board")
    public BoardDto board() {
        List<LocationDto> stops = new ArrayList<>(board.size());
        for (int i = 0; i < board.size(); i++) {
            int[] cell = board.cellOf(i);
            Location stop = board.locationAt(cell[0], cell[1]);
            stops.add(new LocationDto(stop.name(), stop.displayName(), i, cell[0], cell[1]));
        }
        return new BoardDto(stops,
                new BoardDto.TravelDto(costs.travelPerStepMinutes(), costs.enterBuildingMinutes()));
    }
}
