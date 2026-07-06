package amiss.api.security;

import amiss.api.error.InvalidCredentialsException;
import amiss.api.error.InvalidRegistrationException;
import amiss.api.error.UsernameTakenException;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import amiss.domain.validation.Validation;
import amiss.infrastructure.security.PasswordHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Register/login for the REST API (KAN-36) — shares exactly one credential rule with the
 * Swing client's {@code LoginGUI}: both hash/verify through {@link PasswordHasher} over the
 * same {@link UserRepository} port (see {@link SecurityConfig}'s javadoc for why there is no
 * separate Spring Security {@code UserDetailsService}).
 */
@Component
public class AuthService {

    /** {@code iss} claim minted into every access token. */
    static final String ISSUER = "amiss-api";

    private final UserRepository users;
    private final UserStatsRepository userStats;
    private final JwtEncoder jwtEncoder;
    private final Duration ttl;

    public AuthService(UserRepository users, UserStatsRepository userStats, JwtEncoder jwtEncoder,
            @Value("${amiss.security.jwt.ttl:PT60M}") Duration ttl) {
        this.users = users;
        this.userStats = userStats;
        this.jwtEncoder = jwtEncoder;
        this.ttl = ttl;
    }

    /**
     * Creates a brand-new player: validate, reject an already-taken username, then insert the
     * hashed password followed by the starting stats row — the exact order {@code LoginGUI}'s
     * create-user flow uses ({@code insertNewUser} then {@code insertNewStats}).
     *
     * @throws InvalidRegistrationException if the username/password fails {@link Validation} (400)
     * @throws UsernameTakenException       if a password hash is already stored for this username (409)
     */
    public void register(String username, String password) {
        if (!Validation.isValidUsername(username)) {
            throw new InvalidRegistrationException(
                    "Username must be 1-50 characters: letters, digits, and underscores only");
        }
        if (!Validation.isValidPassword(password)) {
            throw new InvalidRegistrationException(
                    "Password must be at least " + Validation.MIN_PASSWORD_LENGTH + " characters");
        }
        if (users.findPasswordHash(username).isPresent()) {
            throw new UsernameTakenException(username);
        }
        users.insertNewUser(username, PasswordHasher.hash(password));
        userStats.insertNewStats(username);
    }

    /**
     * Verifies the password — transparently upgrading a legacy plaintext row exactly like
     * {@code LoginGUI} does — then mints a short-lived HS256 access token.
     *
     * @throws InvalidCredentialsException if the username is unknown or the password is wrong (401);
     *         the same generic message either way, so the response never reveals which
     */
    public AuthResult login(String username, String password) {
        Optional<String> stored = users.findPasswordHash(username);
        if (stored.isEmpty() || !PasswordHasher.matches(password, stored.get())) {
            throw new InvalidCredentialsException();
        }
        if (PasswordHasher.needsRehash(stored.get())) {
            users.updatePassword(username, PasswordHasher.hash(password));
        }
        return new AuthResult(mintToken(username), ttl.getSeconds());
    }

    private String mintToken(String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(username)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
