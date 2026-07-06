package amiss.api.security;

/** The minted access token plus its remaining lifetime, returned by {@link AuthService#login}. */
public record AuthResult(String accessToken, long expiresInSeconds) {
}
