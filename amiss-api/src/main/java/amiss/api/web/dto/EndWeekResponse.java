package amiss.api.web.dto;

/**
 * The result of {@code POST .../end-week}: what the rollover did, plus the save's fresh
 * state. {@code debtCharged} means the closed round was a rent round with rent unpaid, so
 * the late-rent debt was applied; {@code rentDue} means the new round is a rent round;
 * {@code won} mirrors the state's sticky win flag at the moment goals were last checked
 * — after {@code economy}'s consequences (a crash can wipe the win before it's reported).
 */
public record EndWeekResponse(
        int round,
        boolean fed,
        boolean rentDue,
        boolean debtCharged,
        boolean won,
        EconomyEventDto economy,
        SaveStateDto state) {
}
