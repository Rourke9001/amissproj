package amiss.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.CostsConfig;
import amiss.api.config.GameServicesFactory;
import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.PlayerStateDto;
import amiss.application.port.UserRepository;
import amiss.application.service.GameServices;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The KAN-37 acceptance contract, in one slice: anonymous callers get 401, an authenticated
 * player reading/writing someone else's state gets 403 without ever touching the game
 * services, an authenticated player reaches their own state fine, the public trio
 * ({@code /api/highscores}) stays open, and CORS preflight is only honoured for an allowed
 * origin. {@link BoardController}, {@link PlayerController} and {@link HighscoresController}
 * are loaded together purely so every scenario in the acceptance criteria fits in one test
 * class; none of their behaviour beyond routing/security is under test here (see each
 * controller's own {@code *ControllerTest} for that).
 */
@WebMvcTest(controllers = {BoardController.class, PlayerController.class, HighscoresController.class})
@Import({CostsConfig.class, GlobalExceptionHandler.class, SecurityConfig.class})
class SecurityRulesTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GameServicesFactory factory;

    @MockitoBean
    private PlayerStateAssembler assembler;

    @MockitoBean
    private UserRepository users;

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DISALLOWED_ORIGIN = "http://evil.example";

    // ---- authentication: anonymous callers are rejected ---------------------------

    @Test
    void anonymousBoard_is401Unauthenticated() throws Exception {
        mvc.perform(get("/api/board"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unauthenticated"));
    }

    @Test
    void anonymousPlayerState_is401Unauthenticated() throws Exception {
        mvc.perform(get("/api/players/alice"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unauthenticated"));
    }

    // ---- player scoping: authenticated, but not your own state ---------------------

    @Test
    void authenticatedAsAlice_readingBobsState_is403AndNeverReachesTheController() throws Exception {
        mvc.perform(get("/api/players/bob").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(factory, assembler);
    }

    @Test
    void authenticatedAsAlice_movingBob_is403AndNeverReachesTheController() throws Exception {
        mvc.perform(post("/api/players/bob/move")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"BANK\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(factory, assembler);
    }

    @Test
    void authenticatedAsAlice_readingOwnState_is200() throws Exception {
        GameServices services = mock(GameServices.class);
        when(factory.forPlayer("alice")).thenReturn(services);
        when(assembler.assemble("alice", services)).thenReturn(dto("alice"));

        mvc.perform(get("/api/players/alice").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    private static PlayerStateDto dto(String username) {
        return new PlayerStateDto(username, 1, 3600, "60h", false, 120, 0, 0, false,
                1, 1, null, null, null,
                new amiss.api.web.dto.LocationDto("LOW_COST_HOUSING", "Low-Cost Housing", 0, 0, 2));
    }

    // ---- the public trio stays public -----------------------------------------------

    @Test
    void anonymousHighscores_is200() throws Exception {
        when(users.highScores()).thenReturn(java.util.List.of());

        mvc.perform(get("/api/highscores"))
                .andExpect(status().isOk());
    }

    // ---- CORS preflight ---------------------------------------------------------------

    @Test
    void corsPreflight_fromAllowedOrigin_is200WithTheOriginEchoed() throws Exception {
        mvc.perform(options("/api/board")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void corsPreflight_fromDisallowedOrigin_isRejected() throws Exception {
        mvc.perform(options("/api/board")
                        .header("Origin", DISALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
