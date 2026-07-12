package amiss.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.GoalDto;
import amiss.api.web.dto.GoalsDto;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.SaveStateDto;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.model.SaveState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The KAN-37/KAN-54 acceptance contract, in one slice: anonymous callers get 401, an
 * authenticated player reading/writing someone else's save gets 403 without ever touching
 * the save-scoped services, an authenticated player reaches their own save fine, and CORS
 * preflight is only honoured for an allowed origin. {@link BoardController} and {@link
 * PlayerController} are loaded together purely so every scenario fits in one test class; none
 * of their behaviour beyond routing/security is under test here (see each controller's own
 * {@code *ControllerTest}).
 */
@WebMvcTest(controllers = {BoardController.class, PlayerController.class})
@Import({CostsConfig.class, GlobalExceptionHandler.class, SecurityConfig.class})
class SecurityRulesTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaveScope scope;

    @MockitoBean
    private SaveGameServices services;

    @MockitoBean
    private PlayerStateAssembler assembler;

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
    void anonymousSaveState_is401Unauthenticated() throws Exception {
        mvc.perform(get("/api/saves/7"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unauthenticated"));
    }

    // ---- save scoping: authenticated, but not your own save ------------------------

    @Test
    void authenticatedAsAlice_readingBobsSave_is403AndNeverReachesTheServices() throws Exception {
        when(scope.require(eq(7L), any())).thenThrow(new AccessDeniedException("You may only access your own save"));

        mvc.perform(get("/api/saves/7").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(services, assembler);
    }

    @Test
    void authenticatedAsAlice_movingBobsSave_is403AndNeverReachesTheServices() throws Exception {
        when(scope.require(eq(7L), any())).thenThrow(new AccessDeniedException("You may only access your own save"));

        mvc.perform(post("/api/saves/7/move")
                        .with(jwt().jwt(j -> j.subject("alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"BANK\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(services, assembler);
    }

    @Test
    void authenticatedAsAlice_readingOwnSave_is200() throws Exception {
        SaveState save = new SaveState(7L, "alice", "My Save", 0, 2, 3600, 1, 120, 0, 0, 1, 1, 1,
                null, 0, 10, 20, null, 0, 200, 100, 30, 50, false);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(get("/api/saves/7").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    private static SaveStateDto dto() {
        GoalDto goal = new GoalDto(0, 1);
        return new SaveStateDto(7L, "My Save", 1, 3600, "60h", false, 120, 0, 0, false,
                1, 1, null, new LocationDto("LOW_COST_HOUSING", "Low-Cost Housing", 0, 0, 2),
                List.of(), null, new GoalsDto(goal, goal, goal, goal), false);
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
