package amiss.api.web.dto;

/** {@code 200} response body for {@code POST /api/auth/login}. */
public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
