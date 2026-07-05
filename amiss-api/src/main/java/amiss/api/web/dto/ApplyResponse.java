package amiss.api.web.dto;

/**
 * The outcome of a job application. {@code hired=false} with {@code reason=
 * "INSUFFICIENT_EDUCATION"} still reports {@code minutesCharged} — Swing parity: applying
 * always takes the full application time, win or lose.
 */
public record ApplyResponse(boolean hired, String reason, int minutesCharged, String job,
        Integer hourlyWage, PlayerStateDto state) {
}
