package amiss.api.web.dto;

/**
 * One stop of the 13-stop board ring, with its render cell — shared by the player state
 * and the board endpoints. {@code id} is the {@code domain.board.Location} enum name.
 */
public record LocationDto(String id, String name, int ringIndex, int row, int col) {
}
