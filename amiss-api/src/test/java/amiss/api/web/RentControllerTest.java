package amiss.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.GameServicesFactory;
import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.PlayerStateDto;
import amiss.application.config.ActionCosts;
import amiss.application.service.GameServices;
import amiss.application.service.RentPayment;
import amiss.application.service.RentService;
import amiss.application.service.TravelService;
import amiss.domain.board.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The KAN-31 rent endpoint: every rejection status maps to its own problem type. */
@WebMvcTest(RentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class RentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GameServicesFactory factory;

    @MockitoBean
    private PlayerStateAssembler assembler;

    private static PlayerStateDto dto() {
        return new PlayerStateDto("bob", 4, 4200, "70h", false, 20, 0, 0, false,
                1, 1, null, null, null,
                new LocationDto("RENT_OFFICE", "Rent Office", 12, 0, 1));
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

    private void stubPayRent(GameServices services, RentPayment payment) {
        RentService rent = mock(RentService.class);
        when(services.rent()).thenReturn(rent);
        when(rent.payRent()).thenReturn(payment);
    }

    @Test
    void pay_returnsTheAmountAndFreshStateOnSuccess() throws Exception {
        GameServices services = mockServicesAt(Location.RENT_OFFICE);
        stubPayRent(services, new RentPayment(RentPayment.Status.OK, 4200, 20));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(post("/api/players/bob/rent/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountPaid").value(RentService.WEEKLY_RENT))
                .andExpect(jsonPath("$.minutesCharged").value(120))
                .andExpect(jsonPath("$.state.username").value("bob"));
    }

    @Test
    void pay_wrongLocationIsA409Problem() throws Exception {
        mockServicesAt(Location.PAWN_SHOP);

        mvc.perform(post("/api/players/bob/rent/pay"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void pay_notDueIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.RENT_OFFICE);
        stubPayRent(services, new RentPayment(RentPayment.Status.NOT_DUE, 4320, 100));

        mvc.perform(post("/api/players/bob/rent/pay"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:rent-not-due"));
    }

    @Test
    void pay_weekOverIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.RENT_OFFICE);
        stubPayRent(services, new RentPayment(RentPayment.Status.WEEK_OVER, 0, 100));

        mvc.perform(post("/api/players/bob/rent/pay"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void pay_insufficientCashIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.RENT_OFFICE);
        stubPayRent(services, new RentPayment(RentPayment.Status.INSUFFICIENT_CASH, 300, 50));

        mvc.perform(post("/api/players/bob/rent/pay"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void pay_insufficientTimeIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.RENT_OFFICE);
        stubPayRent(services, new RentPayment(RentPayment.Status.INSUFFICIENT_TIME, 50, 100));

        mvc.perform(post("/api/players/bob/rent/pay"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }
}
