package amiss.api.web.dto;

/**
 * The result of {@code POST .../end-week}: what the rollover did, plus the player's fresh
 * state. {@code debtCharged} means the closed round was a rent round with rent unpaid, so
 * the late-rent debt was applied; {@code rentDue} means the new round is a rent round.
 */
public record EndWeekResponse(
        int round,
        boolean fed,
        boolean rentDue,
        boolean debtCharged,
        PlayerStateDto state) {
}
