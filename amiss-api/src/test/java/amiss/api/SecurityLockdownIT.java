package amiss.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.api.persistence.jpa.UserJpaRepository;
import amiss.api.web.dto.LoginRequest;
import amiss.api.web.dto.LoginResponse;
import amiss.api.web.dto.PlayerStateDto;
import amiss.api.web.dto.RegisterRequest;
import amiss.api.web.dto.RegisterResponse;
import amiss.application.port.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The KAN-37 acceptance test, end to end against the real filter chain and a real MySQL
 * container (no mocks): anonymous access is rejected, a player can read their own state,
 * and a valid token for one player is refused — read <em>and</em> write — against another
 * player's state, with the victim's row left untouched. See {@link AuthRoundTripSupport} for
 * why this shares the {@code @DataJpaTest} suites' single Testcontainers MySQL instance.
 */
class SecurityLockdownIT extends AuthRoundTripSupport {

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

    private String register(String suffix) {
        String username = "kan37it" + suffix;
        createdUsers.add(username);
        ResponseEntity<RegisterResponse> response = rest.postForEntity(
                "/api/auth/register", new RegisterRequest(username, "secret1"), RegisterResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return username;
    }

    private String login(String username) {
        ResponseEntity<LoginResponse> response = rest.postForEntity(
                "/api/auth/login", new LoginRequest(username, "secret1"), LoginResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().accessToken();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void anonymousGetPlayerState_is401() {
        String alice = register("alice1");

        ResponseEntity<String> response = rest.getForEntity("/api/players/" + alice, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void ownToken_canReadOwnPlayerState() {
        String alice = register("alice2");
        String token = login(alice);

        ResponseEntity<PlayerStateDto> response = rest.exchange(
                "/api/players/" + alice, HttpMethod.GET, new HttpEntity<>(bearer(token)), PlayerStateDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(alice, response.getBody().username());
    }

    @Test
    void anotherPlayersToken_cannotReadThisPlayersState() {
        String alice = register("alice3");
        String bob = register("bob3");
        String aliceToken = login(alice);

        ResponseEntity<String> response = rest.exchange(
                "/api/players/" + bob, HttpMethod.GET, new HttpEntity<>(bearer(aliceToken)), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().contains("urn:amiss:forbidden"));
    }

    @Test
    void anotherPlayersToken_cannotMutateThisPlayersState_andTheRowIsUnchanged() {
        String alice = register("alice4");
        String bob = register("bob4");
        String aliceToken = login(alice);

        int xBefore = users.getXpos(bob);
        int yBefore = users.getYpos(bob);
        int timeBefore = users.getTime(bob);

        HttpHeaders headers = bearer(aliceToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/api/players/" + bob + "/move", HttpMethod.POST,
                new HttpEntity<>("{\"target\":\"BANK\"}", headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().contains("urn:amiss:forbidden"));
        assertEquals(xBefore, users.getXpos(bob), "bob's saved position must be untouched by alice's blocked move");
        assertEquals(yBefore, users.getYpos(bob), "bob's saved position must be untouched by alice's blocked move");
        assertEquals(timeBefore, users.getTime(bob), "bob's saved clock must be untouched by alice's blocked move");
    }

    @Test
    void anonymousHighscores_staysPublic() {
        ResponseEntity<String> response = rest.getForEntity("/api/highscores", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void anonymousHealth_isStatusOnly() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"UP\""));
        assertFalse(response.getBody().contains("components"),
                "the public health probe must not leak the component breakdown");
    }
}
