package amiss.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.GameServicesFactory;
import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.PlayerStateDto;
import amiss.application.config.ActionCosts;
import amiss.application.port.JobRepository;
import amiss.application.port.PersistenceFailureException;
import amiss.application.service.ApplyOutcome;
import amiss.application.service.GameServices;
import amiss.application.service.JobService;
import amiss.application.service.StatsService;
import amiss.application.service.TravelService;
import amiss.application.service.WorkOutcome;
import amiss.domain.board.Location;
import amiss.domain.model.JobListing;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The KAN-32 employment endpoints: job catalog, applying, and working. */
@WebMvcTest(EmploymentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class EmploymentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GameServicesFactory factory;

    @MockitoBean
    private PlayerStateAssembler assembler;

    @MockitoBean
    private JobRepository jobRepository;

    private static PlayerStateDto dto() {
        return new PlayerStateDto("bob", 3, 3960, "66h", false, 70, 0, 0, false,
                1, 1, null, null, null,
                new LocationDto("EMPLOYMENT_OFFICE", "Employment Office", 7, 3, 3));
    }

    private static MockHttpServletRequestBuilder postJob(String path, String job) {
        return post(path)
                .with(jwt().jwt(j -> j.subject("bob")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"job\":\"" + job + "\"}");
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

    // ---- GET /api/jobs ------------------------------------------------------

    @Test
    void jobs_returnsTheCatalogMapping() throws Exception {
        when(jobRepository.listAll()).thenReturn(List.of(
                new JobListing("Cook", 0, 6, "Monolith Burgers", 1),
                new JobListing("Clerk", 1, 10, "Socket City", 2)));

        mvc.perform(get("/api/jobs").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Cook"))
                .andExpect(jsonPath("$[0].requiredEducation").value(0))
                .andExpect(jsonPath("$[0].hourlyWage").value(6))
                .andExpect(jsonPath("$[0].location").value("Monolith Burgers"))
                .andExpect(jsonPath("$[0].requiredClothing").value(1));
    }

    @Test
    void jobs_emptyCatalogReturnsEmptyList() throws Exception {
        when(jobRepository.listAll()).thenReturn(List.of());

        mvc.perform(get("/api/jobs").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void jobs_sqlExceptionIsA500Problem() throws Exception {
        when(jobRepository.listAll()).thenThrow(new PersistenceFailureException(new SQLException("db down")));

        mvc.perform(get("/api/jobs").with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:persistence-failure"));
    }

    // ---- POST /api/players/{u}/jobs/apply ------------------------------------

    @Test
    void apply_unknownJobIsA400Problem() throws Exception {
        GameServices services = mockServicesAt(Location.EMPLOYMENT_OFFICE);
        JobService jobs = mock(JobService.class);
        when(services.jobs()).thenReturn(jobs);
        when(jobs.apply("Astronaut")).thenReturn(new ApplyOutcome(ApplyOutcome.Status.UNKNOWN_JOB, -1, "Astronaut", -1));

        mvc.perform(postJob("/api/players/bob/jobs/apply", "Astronaut"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-job"));
    }

    @Test
    void apply_wrongLocationIsA409Problem() throws Exception {
        mockServicesAt(Location.PAWN_SHOP);

        mvc.perform(postJob("/api/players/bob/jobs/apply", "Cook"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void apply_weekOverIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.EMPLOYMENT_OFFICE);
        JobService jobs = mock(JobService.class);
        when(services.jobs()).thenReturn(jobs);
        when(jobs.apply("Cook")).thenReturn(new ApplyOutcome(ApplyOutcome.Status.WEEK_OVER, 0, "Cook", -1));

        mvc.perform(postJob("/api/players/bob/jobs/apply", "Cook"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void apply_insufficientTimeIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.EMPLOYMENT_OFFICE);
        JobService jobs = mock(JobService.class);
        when(services.jobs()).thenReturn(jobs);
        when(jobs.apply("Cook")).thenReturn(new ApplyOutcome(ApplyOutcome.Status.INSUFFICIENT_TIME, 50, "Cook", -1));

        mvc.perform(postJob("/api/players/bob/jobs/apply", "Cook"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }

    @Test
    void apply_insufficientEducationIsA200WithHiredFalse() throws Exception {
        GameServices services = mockServicesAt(Location.EMPLOYMENT_OFFICE);
        JobService jobs = mock(JobService.class);
        when(services.jobs()).thenReturn(jobs);
        when(jobs.apply("Store Manager"))
                .thenReturn(new ApplyOutcome(ApplyOutcome.Status.INSUFFICIENT_EDUCATION, 3720, "Store Manager", -1));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(postJob("/api/players/bob/jobs/apply", "Store Manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hired").value(false))
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_EDUCATION"))
                .andExpect(jsonPath("$.minutesCharged").value(240))
                .andExpect(jsonPath("$.job").value("Store Manager"))
                .andExpect(jsonPath("$.hourlyWage").doesNotExist());
    }

    @Test
    void apply_hiredIsA200() throws Exception {
        GameServices services = mockServicesAt(Location.EMPLOYMENT_OFFICE);
        JobService jobs = mock(JobService.class);
        when(services.jobs()).thenReturn(jobs);
        when(jobs.apply("Cook")).thenReturn(new ApplyOutcome(ApplyOutcome.Status.HIRED, 3720, "Cook", 6));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(postJob("/api/players/bob/jobs/apply", "Cook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hired").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.minutesCharged").value(240))
                .andExpect(jsonPath("$.job").value("Cook"))
                .andExpect(jsonPath("$.hourlyWage").value(6));
    }

    // ---- POST /api/players/{u}/work ------------------------------------------

    private GameServices mockServicesWithJob(String jobName, String jobLocation, Location current) {
        GameServices services = mock(GameServices.class);
        JobService jobs = mock(JobService.class);
        TravelService travel = mock(TravelService.class);
        when(factory.forPlayer("bob")).thenReturn(services);
        when(services.jobs()).thenReturn(jobs);
        when(jobs.getJob()).thenReturn(jobName);
        when(jobs.getLocation()).thenReturn(jobLocation);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation()).thenReturn(current);
        when(services.costs()).thenReturn(ActionCosts.defaults());
        return services;
    }

    @Test
    void work_noJobIsA409Problem() throws Exception {
        mockServicesWithJob("Unemployed", null, Location.MONOLITH_BURGERS);

        mvc.perform(post("/api/players/bob/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:no-job"));
    }

    @Test
    void work_wrongLocationIsA409Problem() throws Exception {
        mockServicesWithJob("Cook", "Monolith Burgers", Location.PAWN_SHOP);

        mvc.perform(post("/api/players/bob/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void work_underdressedIsA409Problem() throws Exception {
        GameServices services = mockServicesWithJob("Cook", "Monolith Burgers", Location.MONOLITH_BURGERS);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.work()).thenReturn(new WorkOutcome(WorkOutcome.Status.UNDERDRESSED, -1, -1, "Cook", 6, false));

        mvc.perform(post("/api/players/bob/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:underdressed"));
    }

    @Test
    void work_insufficientTimeIsA409Problem() throws Exception {
        GameServices services = mockServicesWithJob("Cook", "Monolith Burgers", Location.MONOLITH_BURGERS);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.work()).thenReturn(new WorkOutcome(WorkOutcome.Status.INSUFFICIENT_TIME, 50, -1, "Cook", 6, false));

        mvc.perform(post("/api/players/bob/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }

    @Test
    void work_okReturnsWagesAndFreshState() throws Exception {
        GameServices services = mockServicesWithJob("Cook", "Monolith Burgers", Location.MONOLITH_BURGERS);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.work()).thenReturn(new WorkOutcome(WorkOutcome.Status.OK, 3600, 76, "Cook", 6, false));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(post("/api/players/bob/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("Cook"))
                .andExpect(jsonPath("$.hourlyWage").value(6))
                .andExpect(jsonPath("$.minutesCharged").value(360))
                .andExpect(jsonPath("$.debtDocked").value(false))
                .andExpect(jsonPath("$.state.username").value("bob"));
    }
}
