package amiss.api.web;

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

import amiss.api.config.CostsConfig;
import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.GoalDto;
import amiss.api.web.dto.GoalsDto;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.SaveStateDto;
import amiss.application.service.save.CourseService;
import amiss.application.service.save.SaveGameServices;
import amiss.application.service.save.TravelService;
import amiss.domain.board.Location;
import amiss.domain.model.DegreeSpec;
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

/** The KAN-54 university endpoints: the save's course board, enrolling, and studying. */
@WebMvcTest(UniversityController.class)
@Import({GlobalExceptionHandler.class, CostsConfig.class, SecurityConfig.class})
class UniversityControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaveScope scope;
    @MockitoBean
    private SaveGameServices services;
    @MockitoBean
    private PlayerStateAssembler assembler;

    private static SaveState save() {
        return new SaveState(7L, "bob", "My Save", 3, 3, 3960, 3, 70, 0, 0, 0, 1, 1,
                null, 60, 30, 40, null, 0, 200, 100, 30, 50, false, (byte) 0, (short) 0, null);
    }

    private static SaveStateDto dto() {
        GoalDto goal = new GoalDto(0, 1);
        return new SaveStateDto(7L, "My Save", 3, 3960, "66h", false, 70, 0, 0, false,
                1, 1, null, new LocationDto("HI_TECH_U", "Hi-Tech U", 6, 0, 4),
                List.of(), null, new GoalsDto(goal, goal, goal, goal), false);
    }

    private TravelService mockTravelAt(SaveState save, Location location) {
        TravelService travel = mock(TravelService.class);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation(save)).thenReturn(location);
        return travel;
    }

    // ---- GET /api/saves/{id}/courses -------------------------------------------

    @Test
    void courses_returnsTheBoardWithStatusesAndPrereqNames() throws Exception {
        SaveState save = save();
        when(scope.require(eq(7L), any())).thenReturn(save);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.courses(save)).thenReturn(List.of(
                new CourseService.CourseView(new DegreeSpec(1, "Junior College", null),
                        CourseService.CourseStatus.EARNED, false, 0),
                new CourseService.CourseView(new DegreeSpec(2, "Business", 1),
                        CourseService.CourseStatus.AVAILABLE, true, 4)));

        mvc.perform(get("/api/saves/7/courses").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Junior College"))
                .andExpect(jsonPath("$[0].status").value("EARNED"))
                .andExpect(jsonPath("$[0].prereqName").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].prereqName").value("Junior College"))
                .andExpect(jsonPath("$[1].enrolled").value(true))
                .andExpect(jsonPath("$[1].studiesDone").value(4));
    }

    @Test
    void courses_anotherPlayersSaveIsForbiddenAndNeverReachesTheServices() throws Exception {
        when(scope.require(eq(7L), any())).thenThrow(new AccessDeniedException("nope"));

        mvc.perform(get("/api/saves/7/courses").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(services, assembler);
    }

    // ---- POST /api/saves/{id}/courses/{degreeId}/enroll ------------------------

    @Test
    void enroll_wrongLocationIsA409Problem() throws Exception {
        mockTravelAt(save(), Location.PAWN_SHOP);

        mvc.perform(post("/api/saves/7/courses/2/enroll").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void enroll_unknownDegreeIsA400Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.enroll(save, 99)).thenReturn(new CourseService.EnrollResult(
                CourseService.EnrollResult.Status.UNKNOWN_DEGREE, 70, 50));

        mvc.perform(post("/api/saves/7/courses/99/enroll").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-degree"));
    }

    @Test
    void enroll_lockedIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.enroll(save, 2)).thenReturn(new CourseService.EnrollResult(
                CourseService.EnrollResult.Status.LOCKED, 70, 50));

        mvc.perform(post("/api/saves/7/courses/2/enroll").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:degree-locked"));
    }

    @Test
    void enroll_alreadyEarnedIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.enroll(save, 1)).thenReturn(new CourseService.EnrollResult(
                CourseService.EnrollResult.Status.ALREADY_EARNED, 70, 50));

        mvc.perform(post("/api/saves/7/courses/1/enroll").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:degree-already-earned"));
    }

    @Test
    void enroll_alreadyEnrolledIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.enroll(save, 2)).thenReturn(new CourseService.EnrollResult(
                CourseService.EnrollResult.Status.ALREADY_ENROLLED, 70, 50));

        mvc.perform(post("/api/saves/7/courses/2/enroll").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:already-enrolled"));
    }

    @Test
    void enroll_insufficientCashIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.enroll(save, 2)).thenReturn(new CourseService.EnrollResult(
                CourseService.EnrollResult.Status.INSUFFICIENT_CASH, 10, 100));

        mvc.perform(post("/api/saves/7/courses/2/enroll").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void enroll_okReturnsFeeAndFreshState() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.enroll(save, 2)).thenReturn(new CourseService.EnrollResult(
                CourseService.EnrollResult.Status.OK, 20, 50));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(post("/api/saves/7/courses/2/enroll").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feePaid").value(50))
                .andExpect(jsonPath("$.state.id").value(7));
    }

    // ---- POST /api/saves/{id}/courses/study ------------------------------------

    @Test
    void study_wrongLocationIsA409Problem() throws Exception {
        mockTravelAt(save(), Location.PAWN_SHOP);

        mvc.perform(post("/api/saves/7/courses/study").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void study_notEnrolledIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.study(save)).thenReturn(new CourseService.StudyResult(
                CourseService.StudyResult.Status.NOT_ENROLLED, 0, 3960, null));

        mvc.perform(post("/api/saves/7/courses/study").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:not-enrolled"));
    }

    @Test
    void study_weekOverIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.study(save)).thenReturn(new CourseService.StudyResult(
                CourseService.StudyResult.Status.WEEK_OVER, 2, 0, null));

        mvc.perform(post("/api/saves/7/courses/study").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void study_okReturnsProgressAndStudiesRemaining() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.study(save)).thenReturn(new CourseService.StudyResult(
                CourseService.StudyResult.Status.OK, 2, 3600, null));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(post("/api/saves/7/courses/study").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studiesDone").value(2))
                .andExpect(jsonPath("$.studiesRemaining").value(8))
                .andExpect(jsonPath("$.degreeCompleted").doesNotExist())
                .andExpect(jsonPath("$.minutesCharged").value(360));
    }

    @Test
    void study_graduatedReportsZeroRemainingAndTheDegreeName() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.HI_TECH_U);
        CourseService courses = mock(CourseService.class);
        when(services.courses()).thenReturn(courses);
        when(courses.study(save)).thenReturn(new CourseService.StudyResult(
                CourseService.StudyResult.Status.GRADUATED, 10, 3600, "Junior College"));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(post("/api/saves/7/courses/study").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studiesDone").value(10))
                .andExpect(jsonPath("$.studiesRemaining").value(0))
                .andExpect(jsonPath("$.degreeCompleted").value("Junior College"));
    }
}
