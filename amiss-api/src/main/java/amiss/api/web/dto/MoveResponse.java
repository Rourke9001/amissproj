package amiss.api.web.dto;

/** A successful move: where the player went, what it cost, and their fresh state. */
public record MoveResponse(String target, int steps, int minutesCharged, SaveStateDto state) {
}
