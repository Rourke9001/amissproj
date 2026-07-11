package amiss.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.api.persistence.jpa.SaveEntity;
import amiss.api.persistence.jpa.SaveJpaRepository;
import amiss.api.persistence.jpa.UserJpaRepository;
import amiss.api.web.dto.CreateSaveRequest;
import amiss.api.web.dto.LoginRequest;
import amiss.api.web.dto.LoginResponse;
import amiss.api.web.dto.RegisterRequest;
import amiss.api.web.dto.RegisterResponse;
import amiss.api.web.dto.SaveStateDto;
import amiss.api.web.dto.SaveSummaryDto;
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
 * The KAN-37/KAN-54 acceptance test, end to end against the real filter chain and a real
 * MySQL container (no mocks): anonymous access is rejected, a player can read their own
 * save, and a valid token for one player is refused — read <em>and</em> write — against
 * another player's save, with the victim's row left untouched. See {@link
 * AuthRoundTripSupport} for why this shares the {@code @DataJpaTest} suites' single
 * Testcontainers MySQL instance.
 */
class SecurityLockdownIT extends AuthRoundTripSupport {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserJpaRepository userJpa;

    @Autowired
    private SaveJpaRepository saveJpa;

    private final List<String> createdUsers = new ArrayList<>();
    private final List<Long> createdSaves = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long saveId : createdSaves) {
            saveJpa.findById(saveId).ifPresent(saveJpa::delete);
        }
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

    private long createSave(String token) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        CreateSaveRequest request = new CreateSaveRequest("My Save",
                new CreateSaveRequest.GoalTargets(50, 50, 50, 50), null);
        ResponseEntity<SaveSummaryDto> response =
                rest.exchange("/api/saves", HttpMethod.POST, new HttpEntity<>(request, headers), SaveSummaryDto.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        long saveId = response.getBody().id();
        createdSaves.add(saveId);
        return saveId;
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void anonymousGetSaveState_is401() {
        String alice = register("alice1");
        String aliceToken = login(alice);
        long saveId = createSave(aliceToken);

        ResponseEntity<String> response = rest.getForEntity("/api/saves/" + saveId, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void ownToken_canReadOwnSaveState() {
        String alice = register("alice2");
        String token = login(alice);
        long saveId = createSave(token);

        ResponseEntity<SaveStateDto> response = rest.exchange(
                "/api/saves/" + saveId, HttpMethod.GET, new HttpEntity<>(bearer(token)), SaveStateDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(saveId, response.getBody().id());
    }

    @Test
    void anotherPlayersToken_cannotReadThisPlayersSave() {
        String alice = register("alice3");
        String bob = register("bob3");
        String aliceToken = login(alice);
        String bobToken = login(bob);
        long bobSaveId = createSave(bobToken);

        ResponseEntity<String> response = rest.exchange(
                "/api/saves/" + bobSaveId, HttpMethod.GET, new HttpEntity<>(bearer(aliceToken)), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().contains("urn:amiss:forbidden"));
    }

    @Test
    void anotherPlayersToken_cannotMutateThisPlayersSave_andTheRowIsUnchanged() {
        String alice = register("alice4");
        String bob = register("bob4");
        String aliceToken = login(alice);
        String bobToken = login(bob);
        long bobSaveId = createSave(bobToken);

        SaveEntity before = saveJpa.findById(bobSaveId).orElseThrow();
        int xBefore = before.getXpos();
        int yBefore = before.getYpos();
        int timeBefore = before.getTime();

        HttpHeaders headers = bearer(aliceToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/api/saves/" + bobSaveId + "/move", HttpMethod.POST,
                new HttpEntity<>("{\"target\":\"BANK\"}", headers), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().contains("urn:amiss:forbidden"));
        SaveEntity after = saveJpa.findById(bobSaveId).orElseThrow();
        assertEquals(xBefore, after.getXpos(), "bob's saved position must be untouched by alice's blocked move");
        assertEquals(yBefore, after.getYpos(), "bob's saved position must be untouched by alice's blocked move");
        assertEquals(timeBefore, after.getTime(), "bob's saved clock must be untouched by alice's blocked move");
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
