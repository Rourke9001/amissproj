package amiss.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
import amiss.application.service.save.RentPayment;
import amiss.application.service.save.RentService;
import amiss.application.service.save.SaveGameServices;
import amiss.application.service.save.TravelService;
import amiss.domain.board.Location;
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

/** The KAN-54 rent endpoint: every rejection status maps to its own problem type. */
@WebMvcTest(RentController.class)
@Import({GlobalExceptionHandler.class, CostsConfig.class, SecurityConfig.class})
class RentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaveScope scope;
    @MockitoBean
    private SaveGameServices services;
    @MockitoBean
    private PlayerStateAssembler assembler;

    private static SaveState save() {
        return new SaveState(7L, "bob", "My Save", 0, 1, 4200, 4, 20, 0, 0, 1, 1, 1,
                null, 60, 30, 40, null, 0, 200, 100, 30, 50, false);
    }

    private static SaveStateDto dto() {
        GoalDto goal = new GoalDto(0, 1);
        return new SaveStateDto(7L, "My Save", 4, 4200, "70h", false, 20, 0, 0, false,
                1, 1, null, new LocationDto("RENT_OFFICE", "Rent Office", 12, 0, 1),
                List.of(), null, new GoalsDto(goal, goal, goal, goal), false);
    }

    private TravelService mockTravelAt(SaveState save, Location location) {
        TravelService travel = mock(TravelService.class);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation(save)).thenReturn(location);
        return travel;
    }

    private void stubPayRent(SaveState save, RentPayment payment) {
        RentService rent = mock(RentService.class);
        when(services.rent()).thenReturn(rent);
        when(rent.payRent(save)).thenReturn(payment);
    }

    @Test
    void pay_returnsTheAmountAndFreshStateOnSuccess() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.RENT_OFFICE);
        stubPayRent(save, new RentPayment(RentPayment.Status.OK, 4200, 20));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(post("/api/saves/7/rent/pay").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPaid").value(RentService.WEEKLY_RENT))
                .andExpect(jsonPath("$.minutesCharged").value(120))
                .andExpect(jsonPath("$.state.id").value(7));
    }

    @Test
    void pay_wrongLocationIsA409Problem() throws Exception {
        mockTravelAt(save(), Location.PAWN_SHOP);

        mvc.perform(post("/api/saves/7/rent/pay").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void pay_notDueIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.RENT_OFFICE);
        stubPayRent(save, new RentPayment(RentPayment.Status.NOT_DUE, 4320, 100));

        mvc.perform(post("/api/saves/7/rent/pay").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:rent-not-due"));
    }

    @Test
    void pay_weekOverIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.RENT_OFFICE);
        stubPayRent(save, new RentPayment(RentPayment.Status.WEEK_OVER, 0, 100));

        mvc.perform(post("/api/saves/7/rent/pay").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void pay_insufficientCashIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.RENT_OFFICE);
        stubPayRent(save, new RentPayment(RentPayment.Status.INSUFFICIENT_CASH, 300, 50));

        mvc.perform(post("/api/saves/7/rent/pay").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void pay_insufficientTimeIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.RENT_OFFICE);
        stubPayRent(save, new RentPayment(RentPayment.Status.INSUFFICIENT_TIME, 50, 100));

        mvc.perform(post("/api/saves/7/rent/pay").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }

    @Test
    void pay_anotherPlayersSaveIsForbiddenAndNeverReachesTheServices() throws Exception {
        when(scope.require(eq(7L), any())).thenThrow(new AccessDeniedException("nope"));

        mvc.perform(post("/api/saves/7/rent/pay").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(services, assembler);
    }
}
