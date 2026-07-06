package amiss.api.web.dto;

/** {@code 200} response body for {@code GET /api/auth/me}: the authenticated principal's username. */
public record MeResponse(String username) {
}
