package amiss.api.web.dto;

/** A successful enrollment: the fee charged, plus the fresh state. */
public record EnrollResponse(int feePaid, PlayerStateDto state) {
}
