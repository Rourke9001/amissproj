package amiss.api.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.error.SaveNotFoundException;
import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.GoalDto;
import amiss.api.web.dto.GoalsDto;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.SaveStateDto;
import amiss.application.service.save.DoctorVisitOutcome;
import amiss.application.service.save.EconomyEvent;
import amiss.application.service.save.MoveResult;
import amiss.application.service.save.SaveGameServices;
import amiss.application.service.save.TravelService;
import amiss.application.service.save.WeekRolloverService;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The KAN-54 turn endpoints: save state on GET, the explicit end-week transition, and move —
 * cut over from {@code /api/players/{username}} to {@code /api/saves/{saveId}}.
 */
@WebMvcTest(PlayerController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class PlayerControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaveScope scope;
    @MockitoBean
    private SaveGameServices services;
    @MockitoBean
    private PlayerStateAssembler assembler;

    private static SaveState save() {
        return new SaveState(7L, "bob", "My Save", 0, 2, 3960, 3, 120, 50, 0, 0, 1,
                1, 0, 0,
                null, 60, 30, 40, null, 0, 200, 100, 30, 50, false, (byte) 0, (short) 0, null,
                false, Set.of());
    }

    private static SaveStateDto dto() {
        GoalDto goal = new GoalDto(0, 1);
        return new SaveStateDto(7L, "My Save", 3, 3960, "66h", false, 120, 50, 0, false,
                1, false, 1, 0, 0, null, new LocationDto("LOW_COST_HOUSING", "Low-Cost Housing", 0, 0, 2),
                List.of(), null, new GoalsDto(goal, goal, goal, goal), false);
    }

    @Test
    void getState_returnsTheSaveDto() throws Exception {
        SaveState save = save();
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(get("/api/saves/7").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.label").value("My Save"))
                .andExpect(jsonPath("$.timeMinutes").value(3960))
                .andExpect(jsonPath("$.timeDisplay").value("66h"))
                .andExpect(jsonPath("$.weekOver").value(false))
                .andExpect(jsonPath("$.location.id").value("LOW_COST_HOUSING"));
    }

    /**
     * The KAN-54 hidden-requirements contract pin: {@code experience}/{@code dependability}
     * (and every requirement field name) must never appear anywhere in the raw save-state
     * JSON, not just be absent from the fields this test happens to assert on individually.
     */
    @Test
    void getState_wireContractNeverMentionsHiddenStatFields() throws Exception {
        SaveState save = save();
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(get("/api/saves/7").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(matchesPattern(
                        "(?s).*(reqExperience|reqDependability|reqClothing|experience|dependability).*"))));
    }

    @Test
    void getState_unknownSaveIsA404Problem() throws Exception {
        when(scope.require(eq(404L), any())).thenThrow(new SaveNotFoundException(404L));

        mvc.perform(get("/api/saves/404").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:save-not-found"));
    }

    @Test
    void getState_anotherPlayersSaveIsForbiddenAndNeverReachesTheServices() throws Exception {
        when(scope.require(eq(7L), any())).thenThrow(new AccessDeniedException("nope"));

        mvc.perform(get("/api/saves/7").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(services, assembler);
    }

    @Test
    void endWeek_conflictsWhileTimeRemains() throws Exception {
        SaveState save = save();
        when(scope.require(eq(7L), any())).thenReturn(save);
        WeekRolloverService weeks = mock(WeekRolloverService.class);
        when(services.weeks()).thenReturn(weeks);
        when(weeks.endWeek(save)).thenReturn(new WeekRolloverService.RolloverResult(
                false, -1, false, -1, false, false, false, EconomyEvent.none(),
                DoctorVisitOutcome.none()));

        mvc.perform(post("/api/saves/7/end-week").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-not-over"));
    }

    @Test
    void endWeek_returnsTheRolloverSummaryAndFreshState() throws Exception {
        SaveState save = save();
        when(scope.require(eq(7L), any())).thenReturn(save);
        WeekRolloverService weeks = mock(WeekRolloverService.class);
        when(services.weeks()).thenReturn(weeks);
        when(weeks.endWeek(save)).thenReturn(new WeekRolloverService.RolloverResult(
                true, 4, true, 4320, true, false, false, EconomyEvent.none(),
                DoctorVisitOutcome.none()));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(post("/api/saves/7/end-week").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(4))
                .andExpect(jsonPath("$.fed").value(true))
                .andExpect(jsonPath("$.rentDue").value(true))
                .andExpect(jsonPath("$.debtCharged").value(false))
                .andExpect(jsonPath("$.won").value(false))
                .andExpect(jsonPath("$.economy.event").value("NONE"))
                .andExpect(jsonPath("$.economy.severity").value(nullValue()))
                .andExpect(jsonPath("$.doctorVisit.triggered").value(false))
                .andExpect(jsonPath("$.state.id").value(7));
    }

    @Test
    void endWeek_returnsATriggeredDoctorVisit() throws Exception {
        SaveState save = save();
        when(scope.require(eq(7L), any())).thenReturn(save);
        WeekRolloverService weeks = mock(WeekRolloverService.class);
        when(services.weeks()).thenReturn(weeks);
        when(weeks.endWeek(save)).thenReturn(new WeekRolloverService.RolloverResult(
                true, 4, true, 4320, true, false, false, EconomyEvent.none(),
                new DoctorVisitOutcome(true, 600, 4, 30)));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(post("/api/saves/7/end-week").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorVisit.triggered").value(true))
                .andExpect(jsonPath("$.doctorVisit.hoursLost").value(10))
                .andExpect(jsonPath("$.doctorVisit.happinessLost").value(4))
                .andExpect(jsonPath("$.doctorVisit.cashLost").value(30));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder moveTo(String target) {
        return post("/api/saves/7/move")
                .with(jwt().jwt(j -> j.subject("bob")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"target\":\"" + target + "\"}");
    }

    private TravelService mockTravel(SaveState save) {
        TravelService travel = mock(TravelService.class);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(services.travel()).thenReturn(travel);
        return travel;
    }

    @Test
    void move_returnsTheCostAndFreshState() throws Exception {
        SaveState save = save();
        TravelService travel = mockTravel(save);
        when(travel.moveTo(save, Location.BANK)).thenReturn(new MoveResult(MoveResult.Status.OK, 4, 280, 4040));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(moveTo("BANK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value("BANK"))
                .andExpect(jsonPath("$.steps").value(4))
                .andExpect(jsonPath("$.minutesCharged").value(280))
                .andExpect(jsonPath("$.state.id").value(7));
    }

    @Test
    void move_unknownTargetIsA400Problem() throws Exception {
        mvc.perform(moveTo("MOON"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-location"));
    }

    @Test
    void move_insufficientTimeIsA409Problem() throws Exception {
        SaveState save = save();
        TravelService travel = mockTravel(save);
        when(travel.moveTo(save, Location.BANK))
                .thenReturn(new MoveResult(MoveResult.Status.INSUFFICIENT_TIME, 4, 0, 100));

        mvc.perform(moveTo("BANK"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }

    @Test
    void move_weekOverIsA409Problem() throws Exception {
        SaveState save = save();
        TravelService travel = mockTravel(save);
        when(travel.moveTo(save, Location.BANK))
                .thenReturn(new MoveResult(MoveResult.Status.WEEK_OVER, 4, 0, 0));

        mvc.perform(moveTo("BANK"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }
}
