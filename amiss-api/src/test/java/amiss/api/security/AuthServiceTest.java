package amiss.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.api.error.InvalidCredentialsException;
import amiss.api.error.InvalidRegistrationException;
import amiss.api.error.UsernameTakenException;
import amiss.application.port.UserRepository;
import amiss.infrastructure.security.PasswordHasher;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Unit tests for {@link AuthService}: a mocked {@link UserRepository}, but the real {@link
 * PasswordHasher} and a real HS256 {@link JwtEncoder}/{@link JwtDecoder} pair (built from the
 * same in-test secret) so the minted token is decoded and asserted on, not just stubbed.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "test-only-signing-secret-at-least-32-bytes-long";
    private static final Duration TTL = Duration.ofMinutes(45);

    @Mock
    private UserRepository users;

    private JwtDecoder decoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        authService = new AuthService(users, encoder, TTL);
    }

    // ---- register ------------------------------------------------------------------

    @Test
    void register_happyPath_insertsOnlyTheHashedPassword() {
        when(users.findPasswordHash("alice")).thenReturn(Optional.empty());

        authService.register("alice", "secret1");

        verify(users).insertNewUser(eq("alice"), argThat(
                hash -> PasswordHasher.isHashed(hash) && PasswordHasher.matches("secret1", hash)));
    }

    @Test
    void register_invalidUsername_rejectsWithoutTouchingThePorts() {
        assertThrows(InvalidRegistrationException.class, () -> authService.register("bad name!", "secret1"));

        verify(users, never()).insertNewUser(any(), any());
    }

    @Test
    void register_shortPassword_rejectsWithoutTouchingThePorts() {
        assertThrows(InvalidRegistrationException.class, () -> authService.register("alice", "abc"));

        verify(users, never()).insertNewUser(any(), any());
    }

    @Test
    void register_usernameAlreadyTaken_rejectsWithoutTouchingThePorts() {
        when(users.findPasswordHash("alice")).thenReturn(Optional.of(PasswordHasher.hash("existing-pw")));

        assertThrows(UsernameTakenException.class, () -> authService.register("alice", "secret1"));

        verify(users, never()).insertNewUser(any(), any());
    }

    // ---- login ---------------------------------------------------------------------

    @Test
    void login_happyPath_returnsATokenWithTheRightSubjectAndTtl() {
        when(users.findPasswordHash("alice")).thenReturn(Optional.of(PasswordHasher.hash("secret1")));

        Instant before = Instant.now().minusSeconds(2);
        AuthResult result = authService.login("alice", "secret1");
        Instant after = Instant.now().plusSeconds(2);

        Jwt jwt = decoder.decode(result.accessToken());
        assertEquals("alice", jwt.getSubject());
        assertEquals("amiss-api", jwt.getClaimAsString("iss"));
        assertEquals(TTL.getSeconds(), result.expiresInSeconds());
        assertEquals(TTL.getSeconds(), jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond());
        assertTrue(!jwt.getIssuedAt().isBefore(before) && !jwt.getIssuedAt().isAfter(after));

        verify(users, never()).updatePassword(any(), any());
    }

    @Test
    void login_unknownUser_throwsInvalidCredentials() {
        when(users.findPasswordHash("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login("ghost", "whatever"));
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        when(users.findPasswordHash("alice")).thenReturn(Optional.of(PasswordHasher.hash("secret1")));

        assertThrows(InvalidCredentialsException.class, () -> authService.login("alice", "wrong-password"));
    }

    @Test
    void login_legacyPlaintextPassword_succeedsAndUpgradesTheStoredHash() {
        when(users.findPasswordHash("legacy")).thenReturn(Optional.of("plaintext-pw"));

        AuthResult result = authService.login("legacy", "plaintext-pw");

        assertEquals("legacy", decoder.decode(result.accessToken()).getSubject());
        verify(users).updatePassword(eq("legacy"), argThat(
                hash -> PasswordHasher.isHashed(hash) && PasswordHasher.matches("plaintext-pw", hash)));
    }

    @Test
    void login_alreadyBCryptPassword_doesNotUpgradeTheStoredHash() {
        when(users.findPasswordHash("alice")).thenReturn(Optional.of(PasswordHasher.hash("secret1")));

        authService.login("alice", "secret1");

        verify(users, never()).updatePassword(any(), any());
    }
}
