package amiss.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.CostsConfig;
import amiss.api.config.GameServicesFactory;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.PlayerStateDto;
import amiss.application.config.ActionCosts;
import amiss.application.service.EnrollOutcome;
import amiss.application.service.GameServices;
import amiss.application.service.StudyOutcome;
import amiss.application.service.TravelService;
import amiss.application.service.UniversityService;
import amiss.domain.board.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The KAN-32 university endpoints: course catalog, enrolling, and studying. */
@WebMvcTest(UniversityController.class)
@Import({GlobalExceptionHandler.class, CostsConfig.class})
class UniversityControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GameServicesFactory factory;

    @MockitoBean
    private PlayerStateAssembler assembler;

    private static PlayerStateDto dto() {
        return new PlayerStateDto("bob", 3, 3960, "66h", false, 70, 0, 0, false,
                1, 1, null, null, null,
                new LocationDto("HI_TECH_U", "Hi-Tech U", 6, 0, 4));
    }

    private GameServices mockServicesAt(Location location) {
        GameServices services = mock(GameServices.class);
        TravelService travel = mock(TravelService.class);
        when(factory.forPlayer("bob")).thenReturn(services);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation()).thenReturn(location);
        when(services.costs()).thenReturn(ActionCosts.defaults());
        return services;
    }

    // ---- GET /api/courses -----------------------------------------------------

    @Test
    void courses_returnsTheDegreesAndConstants() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degrees.length()").value(8))
                .andExpect(jsonPath("$.degrees[0].level").value(1))
                .andExpect(jsonPath("$.degrees[0].name").value("Junior College"))
                .andExpect(jsonPath("$.degrees[7].level").value(8))
                .andExpect(jsonPath("$.degrees[7].name").value("Publishing"))
                .andExpect(jsonPath("$.enrollFee").value(50))
                .andExpect(jsonPath("$.studiesPerDegree").value(10))
                .andExpect(jsonPath("$.studyMinutes").value(360));
    }

    // ---- POST /api/players/{u}/enroll ------------------------------------------

    @Test
    void enroll_wrongLocationIsA409Problem() throws Exception {
        mockServicesAt(Location.PAWN_SHOP);

        mvc.perform(post("/api/players/bob/enroll"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void enroll_alreadyEnrolledIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.enroll()).thenReturn(new EnrollOutcome(EnrollOutcome.Status.ALREADY_ENROLLED, 120));

        mvc.perform(post("/api/players/bob/enroll"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:already-enrolled"));
    }

    @Test
    void enroll_educationCompleteIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.enroll()).thenReturn(new EnrollOutcome(EnrollOutcome.Status.EDUCATION_COMPLETE, 120));

        mvc.perform(post("/api/players/bob/enroll"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:education-complete"));
    }

    @Test
    void enroll_insufficientCashIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.enroll()).thenReturn(new EnrollOutcome(EnrollOutcome.Status.INSUFFICIENT_CASH, 10));

        mvc.perform(post("/api/players/bob/enroll"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void enroll_weekOverIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.enroll()).thenReturn(new EnrollOutcome(EnrollOutcome.Status.WEEK_OVER, 120));

        mvc.perform(post("/api/players/bob/enroll"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void enroll_okReturnsFeeAndFreshState() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.enroll()).thenReturn(new EnrollOutcome(EnrollOutcome.Status.OK, 70));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(post("/api/players/bob/enroll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feePaid").value(50))
                .andExpect(jsonPath("$.state.username").value("bob"));
    }

    // ---- POST /api/players/{u}/study --------------------------------------------

    @Test
    void study_wrongLocationIsA409Problem() throws Exception {
        mockServicesAt(Location.PAWN_SHOP);

        mvc.perform(post("/api/players/bob/study"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void study_notEnrolledIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.study()).thenReturn(new StudyOutcome(StudyOutcome.Status.NOT_ENROLLED, -1, 0, 0, null));

        mvc.perform(post("/api/players/bob/study"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:not-enrolled"));
    }

    @Test
    void study_educationCompleteIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.study()).thenReturn(new StudyOutcome(StudyOutcome.Status.EDUCATION_COMPLETE, -1, 0, 8, null));

        mvc.perform(post("/api/players/bob/study"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:education-complete"));
    }

    @Test
    void study_insufficientTimeIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.study()).thenReturn(new StudyOutcome(StudyOutcome.Status.INSUFFICIENT_TIME, 50, 2, 0, null));

        mvc.perform(post("/api/players/bob/study"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }

    @Test
    void study_weekOverIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.study()).thenReturn(new StudyOutcome(StudyOutcome.Status.WEEK_OVER, 0, 2, 0, null));

        mvc.perform(post("/api/players/bob/study"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void study_okReturnsProgressAndStudiesRemaining() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.study()).thenReturn(new StudyOutcome(StudyOutcome.Status.OK, 3600, 2, 0, null));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(post("/api/players/bob/study"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(2))
                .andExpect(jsonPath("$.studiesRemaining").value(9))
                .andExpect(jsonPath("$.degreeCompleted").doesNotExist())
                .andExpect(jsonPath("$.educationLevel").value(0))
                .andExpect(jsonPath("$.minutesCharged").value(360));
    }

    @Test
    void study_degreeCompletedReportsZeroRemainingAndTheDegreeName() throws Exception {
        GameServices services = mockServicesAt(Location.HI_TECH_U);
        UniversityService university = mock(UniversityService.class);
        when(services.university()).thenReturn(university);
        when(university.study())
                .thenReturn(new StudyOutcome(StudyOutcome.Status.DEGREE_COMPLETED, 3600, 0, 1, "Junior College"));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(post("/api/players/bob/study"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(0))
                .andExpect(jsonPath("$.studiesRemaining").value(0))
                .andExpect(jsonPath("$.degreeCompleted").value("Junior College"))
                .andExpect(jsonPath("$.educationLevel").value(1));
    }
}
