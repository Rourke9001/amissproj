package amiss.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.CostsConfig;
import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.GoalDto;
import amiss.api.web.dto.GoalsDto;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.SaveStateDto;
import amiss.application.service.save.RelaxService;
import amiss.application.service.save.SaveGameServices;
import amiss.application.service.save.TravelService;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The KAN-23 Relax endpoint: every rejection status maps to its own problem type. */
@WebMvcTest(HomeController.class)
@Import({GlobalExceptionHandler.class, CostsConfig.class, SecurityConfig.class})
class HomeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaveScope scope;
    @MockitoBean
    private SaveGameServices services;
    @MockitoBean
    private PlayerStateAssembler assembler;

    private static SaveState save() {
        return new SaveState(7L, "bob", "My Save", 0, 0, 3600, 1, 100, 0, 0, 1, 0,
                6, 0, 0,
                null, 60, 30, 40, null, 0, 200, 100, 30, 50, false, (byte) 0, (short) 0, null,
                false, Set.of(), 10, false);
    }

    private static SaveStateDto dto() {
        GoalDto goal = new GoalDto(0, 1);
        return new SaveStateDto(7L, "My Save", 1, 3240, "54h", false, 100, 0, 0, false,
                6, false, 1, 0, 0, 13, null, new LocationDto("LOW_COST_HOUSING", "Low-Cost Housing", 0, 0, 2),
                List.of(), null, new GoalsDto(goal, goal, goal, goal), false);
    }

    private TravelService mockTravelAt(SaveState save, Location location) {
        TravelService travel = mock(TravelService.class);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation(save)).thenReturn(location);
        return travel;
    }

    private void stubRelax(SaveState save, RelaxService.RelaxOutcome outcome) {
        RelaxService relax = mock(RelaxService.class);
        when(services.relax()).thenReturn(relax);
        when(relax.relax(save)).thenReturn(outcome);
    }

    @Test
    void relax_returnsMinutesChargedRelaxationAndFreshStateOnSuccess() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.LOW_COST_HOUSING);
        stubRelax(save, new RelaxService.RelaxOutcome(RelaxService.RelaxOutcome.Status.OK, 3240, 13));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(post("/api/saves/7/relax").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutesCharged").value(360))
                .andExpect(jsonPath("$.relaxation").value(13))
                .andExpect(jsonPath("$.state.id").value(7))
                .andExpect(jsonPath("$.state.relaxation").value(13));
    }

    @Test
    void relax_weekOverIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.LOW_COST_HOUSING);
        stubRelax(save, new RelaxService.RelaxOutcome(RelaxService.RelaxOutcome.Status.WEEK_OVER, 0, 10));

        mvc.perform(post("/api/saves/7/relax").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void relax_wrongLocationIsA409Problem() throws Exception {
        mockTravelAt(save(), Location.PAWN_SHOP);

        mvc.perform(post("/api/saves/7/relax").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }
}
