package amiss.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.GameServicesFactory;
import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.PlayerStateDto;
import amiss.application.service.BankService;
import amiss.application.service.BankTransaction;
import amiss.application.service.GameServices;
import amiss.application.service.TimeService;
import amiss.application.service.TimeSpend;
import amiss.application.service.TravelService;
import amiss.domain.board.Location;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The KAN-31 bank endpoints: deposit/withdraw, gated by week-over and location. */
@WebMvcTest(BankController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class BankControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GameServicesFactory factory;

    @MockitoBean
    private PlayerStateAssembler assembler;

    private static PlayerStateDto dto() {
        return new PlayerStateDto("bob", 3, 3960, "66h", false, 70, 50, 0, false,
                1, 1, null, null, null,
                new LocationDto("BANK", "Bank", 9, 2, 0));
    }

    private static MockHttpServletRequestBuilder postAmount(String path, int amount) {
        return post(path)
                .with(jwt().jwt(j -> j.subject("bob")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}");
    }

    private GameServices mockServices(TimeSpend clock, Location location) {
        GameServices services = mock(GameServices.class);
        TimeService time = mock(TimeService.class);
        TravelService travel = mock(TravelService.class);
        when(factory.forPlayer("bob")).thenReturn(services);
        when(services.time()).thenReturn(time);
        when(time.spendMinutes(0)).thenReturn(clock);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation()).thenReturn(location);
        return services;
    }

    @Test
    void deposit_returnsTheUpdatedStateOnSuccess() throws Exception {
        GameServices services = mockServices(new TimeSpend(3960, false, false), Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.deposit(50)).thenReturn(new BankTransaction(BankTransaction.Status.OK, 70, 50));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(postAmount("/api/players/bob/bank/deposit", 50))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("deposit"))
                .andExpect(jsonPath("$.amount").value(50))
                .andExpect(jsonPath("$.state.bank").value(50));
    }

    @Test
    void withdraw_returnsTheUpdatedStateOnSuccess() throws Exception {
        GameServices services = mockServices(new TimeSpend(3960, false, false), Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.withdraw(20)).thenReturn(new BankTransaction(BankTransaction.Status.OK, 90, 30));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(postAmount("/api/players/bob/bank/withdraw", 20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("withdraw"))
                .andExpect(jsonPath("$.amount").value(20));
    }

    @Test
    void deposit_weekOverIsA409ProblemBeforeAnythingElse() throws Exception {
        mockServices(new TimeSpend(0, false, true), Location.BANK);

        mvc.perform(postAmount("/api/players/bob/bank/deposit", 50))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void deposit_wrongLocationIsA409Problem() throws Exception {
        mockServices(new TimeSpend(3960, false, false), Location.PAWN_SHOP);

        mvc.perform(postAmount("/api/players/bob/bank/deposit", 50))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void deposit_invalidAmountIsA400Problem() throws Exception {
        GameServices services = mockServices(new TimeSpend(3960, false, false), Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.deposit(-5)).thenReturn(new BankTransaction(BankTransaction.Status.INVALID_AMOUNT, -1, -1));

        mvc.perform(postAmount("/api/players/bob/bank/deposit", -5))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:invalid-amount"));
    }

    @Test
    void withdraw_insufficientFundsIsA409Problem() throws Exception {
        GameServices services = mockServices(new TimeSpend(3960, false, false), Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.withdraw(500)).thenReturn(new BankTransaction(BankTransaction.Status.INSUFFICIENT_FUNDS, 100, 10));

        mvc.perform(postAmount("/api/players/bob/bank/withdraw", 500))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }
}
