package amiss.api.web.dto;

/**
 * The outcome of eating. {@code ate=false} with {@code reason="INSUFFICIENT_CASH"} still
 * reports {@code minutesCharged} — Swing parity: the hour is charged before cash is checked.
 */
public record EatResponse(String item, int price, boolean ate, String reason, int minutesCharged, PlayerStateDto state) {
}
