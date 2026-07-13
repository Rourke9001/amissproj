package amiss.api.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.GoalDto;
import amiss.api.web.dto.GoalsDto;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.SaveStateDto;
import amiss.application.port.JobCatalog;
import amiss.application.service.save.EconomyService;
import amiss.application.service.save.HireOutcome;
import amiss.application.service.save.HiringService;
import amiss.application.service.save.SaveGameServices;
import amiss.application.service.save.ShiftOutcome;
import amiss.application.service.save.ShiftService;
import amiss.application.service.save.TravelService;
import amiss.domain.board.Location;
import amiss.domain.model.JobSpec;
import amiss.domain.model.SaveState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The KAN-54 employment endpoints: job catalog (V5, requirement-free), applying, working. */
@WebMvcTest(EmploymentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class EmploymentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaveScope scope;
    @MockitoBean
    private SaveGameServices services;
    @MockitoBean
    private PlayerStateAssembler assembler;
    @MockitoBean
    private JobCatalog jobCatalog;

    private static SaveState save(Integer jobId) {
        return new SaveState(7L, "bob", "My Save", 3, 3, 3960, 3, 70, 0, 0, 0, 1, 1,
                jobId, 60, 30, 40, null, 0, 200, 100, 30, 50, false, (byte) 0, (short) 0, null);
    }

    private static SaveStateDto dto() {
        GoalDto goal = new GoalDto(0, 1);
        return new SaveStateDto(7L, "My Save", 3, 3960, "66h", false, 70, 0, 0, false,
                1, 1, null, new LocationDto("EMPLOYMENT_OFFICE", "Employment Office", 7, 3, 3),
                List.of(), null, new GoalsDto(goal, goal, goal, goal), false);
    }

    private static MockHttpServletRequestBuilder postJobId(String path, int jobId) {
        return post(path)
                .with(jwt().jwt(j -> j.subject("bob")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\":" + jobId + "}");
    }

    private HiringService mockHiringAt(SaveState save, Location location) {
        TravelService travel = mock(TravelService.class);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation(save)).thenReturn(location);
        HiringService hiring = mock(HiringService.class);
        when(services.hiring()).thenReturn(hiring);
        return hiring;
    }

    // ---- GET /api/saves/{id}/jobs ------------------------------------------

    /** Stubs a +50% economy (reading 30): base + base*30/60, so adjusted always != base. */
    private EconomyService mockEconomyPlusHalf(SaveState save) {
        EconomyService economy = mock(EconomyService.class);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(services.economy()).thenReturn(economy);
        when(economy.price(anyInt(), eq(save))).thenAnswer(inv -> {
            int base = inv.getArgument(0);
            return base + Math.floorDiv(base * 30, 60);
        });
        return economy;
    }

    @Test
    void jobs_returnsTheRequirementFreeMappingWithEconomyPricedWages() throws Exception {
        mockEconomyPlusHalf(save(null));
        when(jobCatalog.all()).thenReturn(List.of(
                new JobSpec(1, "Cook", "Monolith Burgers", 6, 0, 0, 0),
                new JobSpec(2, "Clerk", "Socket City", 10, 20, 30, 2)));

        mvc.perform(get("/api/saves/7/jobs").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Cook"))
                .andExpect(jsonPath("$[0].location").value("Monolith Burgers"))
                .andExpect(jsonPath("$[0].wage").value(9))
                .andExpect(jsonPath("$[1].wage").value(15))
                .andExpect(jsonPath("$[1].reqExperience").doesNotExist())
                .andExpect(jsonPath("$[1].reqDependability").doesNotExist())
                .andExpect(jsonPath("$[1].reqClothing").doesNotExist())
                .andExpect(jsonPath("$[1].experience").doesNotExist())
                .andExpect(jsonPath("$[1].dependability").doesNotExist());
    }

    @Test
    void jobs_locationFilterDelegatesToByLocation() throws Exception {
        mockEconomyPlusHalf(save(null));
        when(jobCatalog.byLocation("Socket City")).thenReturn(List.of(
                new JobSpec(2, "Clerk", "Socket City", 10, 20, 30, 2)));

        mvc.perform(get("/api/saves/7/jobs").param("location", "Socket City").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Clerk"))
                .andExpect(jsonPath("$[0].wage").value(15));
    }

    /**
     * The KAN-54 hidden-requirements contract pin: not one requirement/hidden-stat field name
     * appears anywhere in the raw JSON, not just absent from the fields this test happens to
     * assert on individually. Also pins that economyIndex/economyReading never appear.
     */
    @Test
    void jobs_wireContractNeverMentionsHiddenRequirementFields() throws Exception {
        mockEconomyPlusHalf(save(null));
        when(jobCatalog.all()).thenReturn(List.of(
                new JobSpec(2, "Clerk", "Socket City", 10, 20, 30, 2)));

        mvc.perform(get("/api/saves/7/jobs").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(matchesPattern(
                        "(?s).*(reqExperience|reqDependability|reqClothing|experience|dependability|economyIndex|economyReading).*"))));
    }

    @Test
    void jobs_emptyCatalogReturnsEmptyList() throws Exception {
        mockEconomyPlusHalf(save(null));
        when(jobCatalog.all()).thenReturn(List.of());

        mvc.perform(get("/api/saves/7/jobs").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- POST /api/saves/{id}/jobs/apply --------------------------------------

    @Test
    void apply_unknownJobIsA400Problem() throws Exception {
        HiringService hiring = mockHiringAt(save(null), Location.EMPLOYMENT_OFFICE);
        when(hiring.apply(any(), eq(999)))
                .thenReturn(new HireOutcome(HireOutcome.Status.UNKNOWN_JOB, List.of(), 0, 3960, null, -1));

        mvc.perform(postJobId("/api/saves/7/jobs/apply", 999))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-job"));
    }

    @Test
    void apply_wrongLocationIsA409Problem() throws Exception {
        mockHiringAt(save(null), Location.PAWN_SHOP);

        mvc.perform(postJobId("/api/saves/7/jobs/apply", 1))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void apply_weekOverIsA409Problem() throws Exception {
        HiringService hiring = mockHiringAt(save(null), Location.EMPLOYMENT_OFFICE);
        when(hiring.apply(any(), eq(1)))
                .thenReturn(new HireOutcome(HireOutcome.Status.WEEK_OVER, List.of(), 0, 0, "Cook", -1));

        mvc.perform(postJobId("/api/saves/7/jobs/apply", 1))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void apply_rejectedReportsEveryReasonAsA200() throws Exception {
        SaveState save = save(null);
        HiringService hiring = mockHiringAt(save, Location.EMPLOYMENT_OFFICE);
        when(hiring.apply(any(), eq(3))).thenReturn(new HireOutcome(HireOutcome.Status.REJECTED,
                List.of(HireOutcome.Reason.NOT_ENOUGH_EDUCATION, HireOutcome.Reason.NOT_ENOUGH_EXPERIENCE),
                240, 3720, "Store Manager", -1));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(postJobId("/api/saves/7/jobs/apply", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hired").value(false))
                .andExpect(jsonPath("$.reasons.length()").value(2))
                .andExpect(jsonPath("$.reasons[0]").value("NOT_ENOUGH_EDUCATION"))
                .andExpect(jsonPath("$.reasons[1]").value("NOT_ENOUGH_EXPERIENCE"))
                .andExpect(jsonPath("$.minutesCharged").value(240))
                .andExpect(jsonPath("$.job").value("Store Manager"))
                .andExpect(jsonPath("$.wage").doesNotExist());
    }

    @Test
    void apply_hiredIsA200() throws Exception {
        SaveState save = save(null);
        HiringService hiring = mockHiringAt(save, Location.EMPLOYMENT_OFFICE);
        when(hiring.apply(any(), eq(1)))
                .thenReturn(new HireOutcome(HireOutcome.Status.HIRED, List.of(), 240, 3720, "Cook", 6));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(postJobId("/api/saves/7/jobs/apply", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hired").value(true))
                .andExpect(jsonPath("$.reasons.length()").value(0))
                .andExpect(jsonPath("$.minutesCharged").value(240))
                .andExpect(jsonPath("$.job").value("Cook"))
                .andExpect(jsonPath("$.wage").value(6));
    }

    // ---- POST /api/saves/{id}/work ---------------------------------------------

    @Test
    void work_noJobIsA409Problem() throws Exception {
        when(scope.require(eq(7L), any())).thenReturn(save(null));

        mvc.perform(post("/api/saves/7/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:no-job"));
    }

    @Test
    void work_wrongLocationIsA409Problem() throws Exception {
        SaveState save = save(1);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(jobCatalog.byId(1)).thenReturn(Optional.of(new JobSpec(1, "Cook", "Monolith Burgers", 6, 0, 0, 0)));
        TravelService travel = mock(TravelService.class);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation(save)).thenReturn(Location.PAWN_SHOP);

        mvc.perform(post("/api/saves/7/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    private ShiftService mockWorkAt(SaveState save, JobSpec job) {
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(jobCatalog.byId(job.id())).thenReturn(Optional.of(job));
        TravelService travel = mock(TravelService.class);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation(save)).thenReturn(Location.MONOLITH_BURGERS);
        ShiftService shifts = mock(ShiftService.class);
        when(services.shifts()).thenReturn(shifts);
        return shifts;
    }

    @Test
    void work_underdressedIsA409Problem() throws Exception {
        SaveState save = save(1);
        JobSpec job = new JobSpec(1, "Cook", "Monolith Burgers", 6, 0, 0, 1);
        ShiftService shifts = mockWorkAt(save, job);
        when(shifts.work(save)).thenReturn(new ShiftOutcome(ShiftOutcome.Status.UNDERDRESSED, false,
                -1, -1, 0, 0, 3960, "Cook"));

        mvc.perform(post("/api/saves/7/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:underdressed"));
    }

    @Test
    void work_firedIsA200WithFiredStatus() throws Exception {
        SaveState save = save(1);
        JobSpec job = new JobSpec(1, "Cook", "Monolith Burgers", 6, 0, 0, 0);
        ShiftService shifts = mockWorkAt(save, job);
        when(shifts.work(save)).thenReturn(new ShiftOutcome(ShiftOutcome.Status.FIRED, false,
                -1, -1, 0, 0, 3960, "Cook"));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(post("/api/saves/7/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FIRED"))
                .andExpect(jsonPath("$.job").value("Cook"));
    }

    @Test
    void work_anotherPlayersSaveIsForbiddenAndNeverReachesTheServices() throws Exception {
        when(scope.require(eq(7L), any())).thenThrow(new AccessDeniedException("nope"));

        mvc.perform(post("/api/saves/7/work").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(services, assembler, jobCatalog);
    }

    @Test
    void work_okReturnsPayoutAndFreshState() throws Exception {
        SaveState save = save(1);
        JobSpec job = new JobSpec(1, "Cook", "Monolith Burgers", 6, 0, 0, 0);
        ShiftService shifts = mockWorkAt(save, job);
        when(shifts.work(save)).thenReturn(new ShiftOutcome(ShiftOutcome.Status.OK, true,
                48, 46, 0, 360, 3600, "Cook"));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(post("/api/saves/7/work").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.warning").value(true))
                .andExpect(jsonPath("$.job").value("Cook"))
                .andExpect(jsonPath("$.pay").value(48))
                .andExpect(jsonPath("$.netPaid").value(46))
                .andExpect(jsonPath("$.minutesCharged").value(360))
                .andExpect(jsonPath("$.state.id").value(7));
    }
}
