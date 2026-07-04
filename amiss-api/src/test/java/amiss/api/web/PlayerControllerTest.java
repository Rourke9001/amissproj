package amiss.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.GameServicesFactory;
import amiss.api.web.dto.PlayerStateDto;
import amiss.application.service.GameServices;
import amiss.application.service.TurnService;
import amiss.application.service.WeekSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The KAN-29 turn endpoints: player state on GET, and the explicit end-week transition —
 * 409 while time remains (no silent rollover), rollover summary + fresh state on success.
 */
@WebMvcTest(PlayerController.class)
@Import(GlobalExceptionHandler.class)
class PlayerControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GameServicesFactory factory;

    @MockitoBean
    private PlayerStateAssembler assembler;

    private static PlayerStateDto dto() {
        return new PlayerStateDto("bob", 3, 3960, "66h", false, 120, 0, false,
                new PlayerStateDto.LocationDto("LOW_COST_HOUSING", "Low-Cost Housing", 0, 0, 2));
    }

    @Test
    void getState_returnsThePlayerDto() throws Exception {
        GameServices services = mock(GameServices.class);
        when(factory.forPlayer("bob")).thenReturn(services);
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(get("/api/players/bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.timeMinutes").value(3960))
                .andExpect(jsonPath("$.timeDisplay").value("66h"))
                .andExpect(jsonPath("$.weekOver").value(false))
                .andExpect(jsonPath("$.location.id").value("LOW_COST_HOUSING"))
                .andExpect(jsonPath("$.location.row").value(0))
                .andExpect(jsonPath("$.location.col").value(2));
    }

    @Test
    void endWeek_conflictsWhileTimeRemains() throws Exception {
        GameServices services = mock(GameServices.class);
        TurnService turn = mock(TurnService.class);
        when(factory.forPlayer("bob")).thenReturn(services);
        when(services.turn()).thenReturn(turn);
        when(turn.endWeek()).thenReturn(WeekSummary.weekStillRunning());

        mvc.perform(post("/api/players/bob/end-week"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-not-over"))
                .andExpect(jsonPath("$.title").value("Week not over"));
    }

    @Test
    void endWeek_returnsTheRolloverSummaryAndFreshState() throws Exception {
        GameServices services = mock(GameServices.class);
        TurnService turn = mock(TurnService.class);
        when(factory.forPlayer("bob")).thenReturn(services);
        when(services.turn()).thenReturn(turn);
        when(turn.endWeek()).thenReturn(new WeekSummary(true, 4, true, 4320, true, false));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(post("/api/players/bob/end-week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(4))
                .andExpect(jsonPath("$.fed").value(true))
                .andExpect(jsonPath("$.rentDue").value(true))
                .andExpect(jsonPath("$.debtCharged").value(false))
                .andExpect(jsonPath("$.state.username").value("bob"));
    }
}
