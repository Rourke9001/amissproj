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

import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.GoalDto;
import amiss.api.web.dto.GoalsDto;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.SaveStateDto;
import amiss.application.service.save.BankService;
import amiss.application.service.save.BankTransaction;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The KAN-54 bank endpoints: deposit/withdraw, gated by location, save-scoped. */
@WebMvcTest(BankController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class BankControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaveScope scope;
    @MockitoBean
    private SaveGameServices services;
    @MockitoBean
    private PlayerStateAssembler assembler;

    private static SaveState save() {
        return new SaveState(7L, "bob", "My Save", 2, 0, 3960, 3, 70, 50, 0, 0, 1,
                1, 0, 0,
                null, 60, 30, 40, null, 0, 200, 100, 30, 50, false, (byte) 0, (short) 0, null,
                false, Set.of(), 10, false);
    }

    private static SaveStateDto dto() {
        GoalDto goal = new GoalDto(0, 1);
        return new SaveStateDto(7L, "My Save", 3, 3960, "66h", false, 70, 50, 0, false,
                1, false, 1, 0, 0, null, new LocationDto("BANK", "Bank", 9, 2, 0),
                List.of(), null, new GoalsDto(goal, goal, goal, goal), false);
    }

    private static MockHttpServletRequestBuilder postAmount(String path, int amount) {
        return post(path)
                .with(jwt().jwt(j -> j.subject("bob")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}");
    }

    private TravelService mockTravelAt(SaveState save, Location location) {
        TravelService travel = mock(TravelService.class);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation(save)).thenReturn(location);
        return travel;
    }

    @Test
    void deposit_returnsTheUpdatedStateOnSuccess() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.deposit(save, 50)).thenReturn(new BankTransaction(BankTransaction.Status.OK, 20, 100));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(postAmount("/api/saves/7/bank/deposit", 50))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("deposit"))
                .andExpect(jsonPath("$.amount").value(50))
                .andExpect(jsonPath("$.state.bank").value(50));
    }

    @Test
    void withdraw_returnsTheUpdatedStateOnSuccess() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.withdraw(save, 20)).thenReturn(new BankTransaction(BankTransaction.Status.OK, 90, 30));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(postAmount("/api/saves/7/bank/withdraw", 20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("withdraw"))
                .andExpect(jsonPath("$.amount").value(20));
    }

    @Test
    void deposit_wrongLocationIsA409Problem() throws Exception {
        mockTravelAt(save(), Location.PAWN_SHOP);

        mvc.perform(postAmount("/api/saves/7/bank/deposit", 50))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void deposit_invalidAmountIsA400Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.deposit(save, -5)).thenReturn(new BankTransaction(BankTransaction.Status.INVALID_AMOUNT, -1, -1));

        mvc.perform(postAmount("/api/saves/7/bank/deposit", -5))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:invalid-amount"));
    }

    @Test
    void withdraw_insufficientFundsIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.withdraw(save, 500)).thenReturn(new BankTransaction(BankTransaction.Status.INSUFFICIENT_FUNDS, 100, 10));

        mvc.perform(postAmount("/api/saves/7/bank/withdraw", 500))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void deposit_weekOverIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.deposit(save, 50)).thenReturn(new BankTransaction(BankTransaction.Status.WEEK_OVER, 70, 50));

        mvc.perform(postAmount("/api/saves/7/bank/deposit", 50))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void withdraw_weekOverIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BANK);
        BankService bank = mock(BankService.class);
        when(services.bank()).thenReturn(bank);
        when(bank.withdraw(save, 20)).thenReturn(new BankTransaction(BankTransaction.Status.WEEK_OVER, 70, 50));

        mvc.perform(postAmount("/api/saves/7/bank/withdraw", 20))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void deposit_anotherPlayersSaveIsForbiddenAndNeverReachesTheServices() throws Exception {
        when(scope.require(eq(7L), any())).thenThrow(new AccessDeniedException("nope"));

        mvc.perform(postAmount("/api/saves/7/bank/deposit", 50))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(services, assembler);
    }
}
