package amiss.api.web.dto;

import java.util.List;

/**
 * The game board on the wire: the 13 ring stops in clockwise order plus the travel cost
 * metadata a client needs to price a move ({@code steps x minutesPerStep +
 * enterBuildingMinutes}).
 */
public record BoardDto(List<LocationDto> stops, TravelDto travel) {

    public record TravelDto(int minutesPerStep, int enterBuildingMinutes) {
    }
}
