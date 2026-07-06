package amiss.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.api.persistence.jpa.UserJpaRepository;
import amiss.api.web.dto.LoginRequest;
import amiss.api.web.dto.LoginResponse;
import amiss.api.web.dto.MeResponse;
import amiss.api.web.dto.RegisterRequest;
import amiss.api.web.dto.RegisterResponse;
import amiss.application.port.UserRepository;
import amiss.infrastructure.security.PasswordHasher;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

/**
 * The KAN-36 acceptance test: register, log in, then call the bearer-protected {@code
 * GET /api/auth/me} — through the real Spring Security filter chain, the real {@link
 * amiss.api.security.AuthService}, and a real, freshly-migrated MySQL container (no mocks
 * anywhere on this path). See {@link AuthRoundTripSupport} for why this {@code @SpringBootTest}
 * shares its container with the {@code @DataJpaTest} suites instead of starting a second one.
 */
class AuthRoundTripIT extends AuthRoundTripSupport {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserJpaRepository userJpa;

    private final List<String> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String name : createdUsers) {
            userJpa.findById(name).ifPresent(userJpa::delete);
        }
    }

    private String newUsername(String suffix) {
        String name = "kan36it" + suffix;
        createdUsers.add(name);
        return name;
    }

    @Test
    void registerThenLoginThenMe_roundTripsAgainstTheRealFilterChainAndDatabase() {
        String username = newUsername("roundtrip");

        ResponseEntity<RegisterResponse> registerResponse = rest.postForEntity(
                "/api/auth/register", new RegisterRequest(username, "secret1"), RegisterResponse.class);
        assertEquals(HttpStatus.CREATED, registerResponse.getStatusCode());
        assertEquals(username, registerResponse.getBody().username());

        ResponseEntity<LoginResponse> loginResponse = rest.postForEntity(
                "/api/auth/login", new LoginRequest(username, "secret1"), LoginResponse.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        LoginResponse login = loginResponse.getBody();
        assertNotNull(login);
        assertEquals("Bearer", login.tokenType());
        assertTrue(login.expiresInSeconds() > 0);

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(login.accessToken());
        ResponseEntity<MeResponse> meResponse = rest.exchange(
                "/api/auth/me", HttpMethod.GET, new HttpEntity<>(authHeaders), MeResponse.class);
        assertEquals(HttpStatus.OK, meResponse.getStatusCode());
        assertEquals(username, meResponse.getBody().username());
    }

    @Test
    void me_withoutAToken_is401() {
        ResponseEntity<String> response = rest.getForEntity("/api/auth/me", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_wrongPassword_is401() {
        String username = newUsername("wrongpw");
        rest.postForEntity("/api/auth/register", new RegisterRequest(username, "secret1"), RegisterResponse.class);

        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest(username, "not-the-password"), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void login_legacyPlaintextAccount_succeedsAndUpgradesTheStoredHash() {
        String username = newUsername("legacy");
        // Seeds a pre-hashing-era row directly through the port, bypassing /register
        // entirely — exactly the shape a real legacy account has (plaintext "hash").
        users.insertNewUser(username, "plaintext-pw");

        ResponseEntity<LoginResponse> loginResponse = rest.postForEntity(
                "/api/auth/login", new LoginRequest(username, "plaintext-pw"), LoginResponse.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());

        String stored = users.findPasswordHash(username).orElseThrow();
        assertTrue(PasswordHasher.isHashed(stored), "legacy plaintext password must be upgraded to a BCrypt hash");
    }

    @Test
    void existingGameEndpoint_staysReachableUnauthenticated() {
        ResponseEntity<String> response = rest.getForEntity("/api/board", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
