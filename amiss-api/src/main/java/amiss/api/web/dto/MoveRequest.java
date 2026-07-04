package amiss.api.web.dto;

/** Body of {@code POST .../move}: the target stop's {@code Location} id, e.g. {@code "BANK"}. */
public record MoveRequest(String target) {
}
