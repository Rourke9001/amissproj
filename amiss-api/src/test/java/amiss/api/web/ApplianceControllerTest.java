package amiss.api.web;

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

import amiss.api.config.CostsConfig;
import amiss.api.security.SecurityConfig;
import amiss.api.web.dto.GoalDto;
import amiss.api.web.dto.GoalsDto;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.SaveStateDto;
import amiss.application.service.save.ApplianceService;
import amiss.application.service.save.EconomyService;
import amiss.application.service.save.PurchaseOutcome;
import amiss.application.service.save.SaveGameServices;
import amiss.application.service.save.TravelService;
import amiss.domain.board.Location;
import amiss.domain.model.ApplianceItem;
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

/** The KAN-23 appliance endpoints: catalog and buying, save-scoped. */
@WebMvcTest(ApplianceController.class)
@Import({GlobalExceptionHandler.class, CostsConfig.class, SecurityConfig.class})
class ApplianceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SaveScope scope;
    @MockitoBean
    private SaveGameServices services;
    @MockitoBean
    private PlayerStateAssembler assembler;

    private static SaveState save() {
        return new SaveState(7L, "bob", "My Save", 1, 4, 3960, 3, 70, 0, 0, 0, 0,
                1, 0, 0,
                null, 60, 30, 40, null, 0, 200, 100, 30, 50, false, (byte) 0, (short) 0, null,
                false, Set.of(), 10, false);
    }

    private static SaveStateDto dto() {
        GoalDto goal = new GoalDto(0, 1);
        return new SaveStateDto(7L, "My Save", 3, 3960, "66h", false, 70, 0, 0, false,
                1, false, 1, 0, 0, 10, null, new LocationDto("SOCKET_CITY", "Socket City", 3, 1, 4),
                List.of(), null, new GoalsDto(goal, goal, goal, goal), false);
    }

    private static MockHttpServletRequestBuilder postBody(String path, String field, String value) {
        return post(path)
                .with(jwt().jwt(j -> j.subject("bob")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + field + "\":\"" + value + "\"}");
    }

    private TravelService mockTravelAt(SaveState save, Location location) {
        TravelService travel = mock(TravelService.class);
        when(scope.require(eq(7L), any())).thenReturn(save);
        when(services.travel()).thenReturn(travel);
        when(travel.currentLocation(save)).thenReturn(location);
        return travel;
    }

    /** A reading of +30 makes every adjusted price {@code base + base/2} (floor), never the base. */
    private static final int READING = 30;

    private void mockEconomyAt(SaveState save) {
        when(scope.require(eq(7L), any())).thenReturn(save);
        EconomyService economy = mock(EconomyService.class);
        when(services.economy()).thenReturn(economy);
        when(economy.price(anyInt(), eq(save))).thenAnswer(invocation -> {
            int base = invocation.getArgument(0);
            return base + Math.floorDiv(base * READING, 60);
        });
    }

    // ---- GET /api/saves/{id}/appliances ------------------------------------------

    @Test
    void catalog_returnsTheCatalogAtEconomyPricesWithStoreAndOwnedFlag() throws Exception {
        SaveState save = save();
        save.grantAppliance(ApplianceItem.FRIDGE);
        mockEconomyAt(save);

        mvc.perform(get("/api/saves/7/appliances").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].id").value("FRIDGE"))
                .andExpect(jsonPath("$[0].price").value(1314))  // base 876 + 438
                .andExpect(jsonPath("$[0].store").value("SOCKET_CITY"))
                .andExpect(jsonPath("$[0].owned").value(true))
                .andExpect(jsonPath("$[1].id").value("FREEZER"))
                .andExpect(jsonPath("$[1].price").value(769))   // base 513 + 256 (floor)
                .andExpect(jsonPath("$[1].store").value("SOCKET_CITY"))
                .andExpect(jsonPath("$[1].owned").value(false))
                .andExpect(jsonPath("$[2].id").value("COMPUTER"))
                .andExpect(jsonPath("$[2].price").value(2398))  // base 1599 + 799 (floor)
                .andExpect(jsonPath("$[2].store").value("SOCKET_CITY"))
                .andExpect(jsonPath("$[2].owned").value(false))
                .andExpect(jsonPath("$[3].id").value("ENCYCLOPEDIA"))
                .andExpect(jsonPath("$[3].price").value(712))   // base 475 + 237 (floor)
                .andExpect(jsonPath("$[3].store").value("Z_MART"))
                .andExpect(jsonPath("$[3].owned").value(false))
                .andExpect(jsonPath("$[4].id").value("DICTIONARY"))
                .andExpect(jsonPath("$[4].price").value(105))   // base 70 + 35
                .andExpect(jsonPath("$[4].store").value("Z_MART"))
                .andExpect(jsonPath("$[4].owned").value(false))
                .andExpect(jsonPath("$[5].id").value("ATLAS"))
                .andExpect(jsonPath("$[5].price").value(82))    // base 55 + 27 (floor)
                .andExpect(jsonPath("$[5].store").value("Z_MART"))
                .andExpect(jsonPath("$[5].owned").value(false));
    }

    // ---- POST /api/saves/{id}/appliances -----------------------------------------

    @Test
    void buy_anotherPlayersSaveIsForbiddenAndNeverReachesTheServices() throws Exception {
        when(scope.require(eq(7L), any())).thenThrow(new AccessDeniedException("nope"));

        mvc.perform(postBody("/api/saves/7/appliances", "item", "FRIDGE"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(services, assembler);
    }

    @Test
    void buy_unknownItemIsA400Problem() throws Exception {
        // TOASTER isn't a real ApplianceItem constant (COMPUTER became one in KAN-23 extra credit).
        mvc.perform(postBody("/api/saves/7/appliances", "item", "TOASTER"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-item"));
    }

    @Test
    void buy_wrongLocationIsA409Problem() throws Exception {
        mockTravelAt(save(), Location.PAWN_SHOP);

        mvc.perform(postBody("/api/saves/7/appliances", "item", "FRIDGE"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void buy_insufficientCashIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.SOCKET_CITY);
        ApplianceService appliances = mock(ApplianceService.class);
        when(services.appliances()).thenReturn(appliances);
        when(appliances.buy(save, ApplianceItem.FRIDGE))
                .thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, 3900, 10, 876));

        mvc.perform(postBody("/api/saves/7/appliances", "item", "FRIDGE"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void buy_alreadyOwnedIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.SOCKET_CITY);
        ApplianceService appliances = mock(ApplianceService.class);
        when(services.appliances()).thenReturn(appliances);
        when(appliances.buy(save, ApplianceItem.FRIDGE))
                .thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.ALREADY_OWNED, 3900, 3900, 876));

        mvc.perform(postBody("/api/saves/7/appliances", "item", "FRIDGE"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:appliance-already-owned"));
    }

    @Test
    void buy_okReturnsItemPricePaidAndFreshState() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.SOCKET_CITY);
        ApplianceService appliances = mock(ApplianceService.class);
        when(services.appliances()).thenReturn(appliances);
        when(appliances.buy(save, ApplianceItem.FRIDGE))
                .thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.OK, 3900, 3024, 876));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(postBody("/api/saves/7/appliances", "item", "FRIDGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item").value("FRIDGE"))
                .andExpect(jsonPath("$.price").value(876))
                .andExpect(jsonPath("$.state.id").value(7));
    }
}
