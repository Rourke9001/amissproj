package amiss.api.web.dto;

/**
 * A player's turn-relevant state on the wire (KAN-29; enriched further by KAN-28).
 * {@code timeMinutes} is the raw clock, {@code timeDisplay} its human form (e.g.
 * {@code "38h 30m"}); while {@code weekOver} is {@code true} every action conflicts (409)
 * until {@code POST .../end-week} starts the next round.
 *
 * @param location where the player stands on the 13-stop board ring; derived from
 *                 {@code domain.board.Board} (a stale saved cell is reported as home)
 */
public record PlayerStateDto(
        String username,
        int round,
        int timeMinutes,
        String timeDisplay,
        boolean weekOver,
        int cash,
        int debt,
        boolean rentDue,
        LocationDto location) {

    /** One stop of the board ring, with its render cell. */
    public record LocationDto(String id, String name, int ringIndex, int row, int col) {
    }
}
