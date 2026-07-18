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
import amiss.application.service.save.EatOutcome;
import amiss.application.service.save.EconomyService;
import amiss.application.service.save.PurchaseOutcome;
import amiss.application.service.save.SaveGameServices;
import amiss.application.service.save.ShopService;
import amiss.application.service.save.TravelService;
import amiss.domain.board.Location;
import amiss.domain.model.FastFoodItem;
import amiss.domain.model.FoodPack;
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

/** The KAN-54 food endpoints: catalog, eating, groceries and clothes, save-scoped. */
@WebMvcTest(FoodController.class)
@Import({GlobalExceptionHandler.class, CostsConfig.class, SecurityConfig.class})
class FoodControllerTest {

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
        return dto(1, false);
    }

    private static SaveStateDto dto(int foodWeeks, boolean ateFastFoodLastTurn) {
        GoalDto goal = new GoalDto(0, 1);
        return new SaveStateDto(7L, "My Save", 3, 3960, "66h", false, 70, 0, 0, false,
                foodWeeks, ateFastFoodLastTurn, 1, 0, 0, null,
                new LocationDto("MONOLITH_BURGERS", "Monolith Burgers", 3, 1, 4),
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

    // ---- GET /api/saves/{id}/food ----------------------------------------------

    @Test
    void catalog_returnsTheMenuAndPacksAtEconomyPrices() throws Exception {
        mockEconomyAt(save());

        mvc.perform(get("/api/saves/7/food").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menu.length()").value(6))
                .andExpect(jsonPath("$.menu[0].id").value("BURGER"))
                .andExpect(jsonPath("$.menu[0].name").value("Burger"))
                .andExpect(jsonPath("$.menu[0].price").value(48))   // base 32 + 16
                .andExpect(jsonPath("$.menu[3].id").value("MILKSHAKE"))
                .andExpect(jsonPath("$.menu[3].price").value(33))   // base 22 + 11
                .andExpect(jsonPath("$.menu[5].id").value("FAMILY_MEAL"))
                .andExpect(jsonPath("$.menu[5].price").value(75))   // base 50 + 25
                .andExpect(jsonPath("$.packs.length()").value(4))
                .andExpect(jsonPath("$.packs[0].id").value("ONE_WEEK"))
                .andExpect(jsonPath("$.packs[0].price").value(37))  // base 25 + 12 (floor)
                .andExpect(jsonPath("$.packs[0].weeks").value(1))
                .andExpect(jsonPath("$.packs[3].id").value("EIGHT_WEEKS"))
                .andExpect(jsonPath("$.packs[3].price").value(210)) // base 140 + 70
                .andExpect(jsonPath("$.packs[3].weeks").value(8));
    }

    // ---- GET /api/saves/{id}/clothes -------------------------------------------

    @Test
    void clothesCatalog_returnsTheStockAtEconomyPrices() throws Exception {
        mockEconomyAt(save());

        mvc.perform(get("/api/saves/7/clothes").with(jwt().jwt(j -> j.subject("bob"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value("CASUAL"))
                .andExpect(jsonPath("$[0].name").value("Casual Clothes"))
                .andExpect(jsonPath("$[0].price").value(109))  // base 73 + 36 (floor)
                .andExpect(jsonPath("$[0].level").value(1))
                .andExpect(jsonPath("$[0].weeks").value(11))
                .andExpect(jsonPath("$[1].id").value("DRESS"))
                .andExpect(jsonPath("$[1].price").value(187))  // base 125 + 62 (floor)
                .andExpect(jsonPath("$[1].level").value(2))
                .andExpect(jsonPath("$[1].weeks").value(13))
                .andExpect(jsonPath("$[2].id").value("BUSINESS"))
                .andExpect(jsonPath("$[2].price").value(442))  // base 295 + 147 (floor)
                .andExpect(jsonPath("$[2].level").value(3))
                .andExpect(jsonPath("$[2].weeks").value(13));
    }

    // ---- POST /api/saves/{id}/eat -----------------------------------------------

    @Test
    void eat_anotherPlayersSaveIsForbiddenAndNeverReachesTheServices() throws Exception {
        when(scope.require(eq(7L), any())).thenThrow(new AccessDeniedException("nope"));

        mvc.perform(postBody("/api/saves/7/eat", "item", "BURGER"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:forbidden"));

        verifyNoInteractions(services, assembler);
    }

    @Test
    void eat_unknownItemIsA400Problem() throws Exception {
        mvc.perform(postBody("/api/saves/7/eat", "item", "CAVIAR"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-item"));
    }

    @Test
    void eat_wrongLocationIsA409Problem() throws Exception {
        mockTravelAt(save(), Location.PAWN_SHOP);

        mvc.perform(postBody("/api/saves/7/eat", "item", "BURGER"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void eat_insufficientTimeIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.MONOLITH_BURGERS);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.eat(save, FastFoodItem.BURGER))
                .thenReturn(new EatOutcome(EatOutcome.Status.INSUFFICIENT_TIME, 50, -1, 32));

        mvc.perform(postBody("/api/saves/7/eat", "item", "BURGER"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }

    @Test
    void eat_insufficientCashIsA200WithAteFalse() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.MONOLITH_BURGERS);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.eat(save, FastFoodItem.BURGER))
                .thenReturn(new EatOutcome(EatOutcome.Status.INSUFFICIENT_CASH, 3900, 10, 32));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(postBody("/api/saves/7/eat", "item", "BURGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item").value("BURGER"))
                .andExpect(jsonPath("$.price").value(32))
                .andExpect(jsonPath("$.ate").value(false))
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_CASH"))
                .andExpect(jsonPath("$.minutesCharged").value(0));
    }

    @Test
    void eat_okReturnsAteTrue() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.MONOLITH_BURGERS);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.eat(save, FastFoodItem.BURGER))
                .thenReturn(new EatOutcome(EatOutcome.Status.OK, 3900, 38, 32));
        when(assembler.assemble(services, save)).thenReturn(dto(1, true));

        mvc.perform(postBody("/api/saves/7/eat", "item", "BURGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ate").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.minutesCharged").value(0))
                .andExpect(jsonPath("$.state.id").value(7))
                .andExpect(jsonPath("$.state.ateFastFoodLastTurn").value(true));
    }

    // ---- POST /api/saves/{id}/groceries -----------------------------------------

    @Test
    void groceries_unknownItemIsA400Problem() throws Exception {
        mvc.perform(postBody("/api/saves/7/groceries", "pack", "FIFTY_WEEKS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-item"));
    }

    @Test
    void groceries_wrongLocationIsA409Problem() throws Exception {
        mockTravelAt(save(), Location.PAWN_SHOP);

        mvc.perform(postBody("/api/saves/7/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void groceries_weekOverIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BLACKS_MARKET);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.buyGroceries(save, FoodPack.TWO_WEEKS))
                .thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.WEEK_OVER, 0, 70, 48));

        mvc.perform(postBody("/api/saves/7/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void groceries_insufficientCashIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BLACKS_MARKET);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.buyGroceries(save, FoodPack.TWO_WEEKS))
                .thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, 3900, 10, 48));

        mvc.perform(postBody("/api/saves/7/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void groceries_insufficientTimeIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BLACKS_MARKET);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.buyGroceries(save, FoodPack.TWO_WEEKS))
                .thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_TIME, 50, 70, 48));

        mvc.perform(postBody("/api/saves/7/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }

    @Test
    void groceries_okReturnsWeeksAddedAndFoodWeeks() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.BLACKS_MARKET);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.buyGroceries(save, FoodPack.TWO_WEEKS)).thenAnswer(invocation -> {
            save.setEat(2);
            return new PurchaseOutcome(PurchaseOutcome.Status.OK, 3900, 22, 48);
        });
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(postBody("/api/saves/7/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pack").value("TWO_WEEKS"))
                .andExpect(jsonPath("$.price").value(48))
                .andExpect(jsonPath("$.weeksAdded").value(2))
                .andExpect(jsonPath("$.foodWeeks").value(2))
                .andExpect(jsonPath("$.state.id").value(7));
    }

    /**
     * KAN-23: storage is capped by fridge/freezer ownership, so {@code weeksAdded} must
     * report the real before/after delta, not the pack's nominal {@code weeks()} size — here
     * an EIGHT_WEEKS pack (nominal 8) only nets +1 because the save is already at 5 and the
     * service clamps the post-purchase total to 6 (a Fridge-owner's cap).
     */
    @Test
    void groceries_weeksAddedIsTheCappedDeltaNotThePackNominalSize() throws Exception {
        SaveState save = save();
        save.setEat(5);
        mockTravelAt(save, Location.BLACKS_MARKET);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.buyGroceries(save, FoodPack.EIGHT_WEEKS)).thenAnswer(invocation -> {
            save.setEat(6);
            return new PurchaseOutcome(PurchaseOutcome.Status.OK, 3760, 22, 140);
        });
        when(assembler.assemble(services, save)).thenReturn(dto(6, false));

        mvc.perform(postBody("/api/saves/7/groceries", "pack", "EIGHT_WEEKS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pack").value("EIGHT_WEEKS"))
                .andExpect(jsonPath("$.weeksAdded").value(1))
                .andExpect(jsonPath("$.foodWeeks").value(6));
    }

    // ---- POST /api/saves/{id}/clothes -------------------------------------------

    @Test
    void clothes_unknownItemIsA400Problem() throws Exception {
        mvc.perform(postBody("/api/saves/7/clothes", "item", "TUXEDO"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-item"));
    }

    @Test
    void clothes_wrongLocationIsA409Problem() throws Exception {
        mockTravelAt(save(), Location.PAWN_SHOP);

        mvc.perform(postBody("/api/saves/7/clothes", "item", "BUSINESS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void clothes_insufficientCashIsA409Problem() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.QT_CLOTHING);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.buyClothes(any(), any()))
                .thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, 3900, 10, 295));

        mvc.perform(postBody("/api/saves/7/clothes", "item", "BUSINESS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void clothes_okReturnsItemPriceAndFreshState() throws Exception {
        SaveState save = save();
        mockTravelAt(save, Location.QT_CLOTHING);
        ShopService shop = mock(ShopService.class);
        when(services.shop()).thenReturn(shop);
        when(shop.buyClothes(any(), any()))
                .thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.OK, 3900, 15, 295));
        when(assembler.assemble(services, save)).thenReturn(dto());

        mvc.perform(postBody("/api/saves/7/clothes", "item", "BUSINESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item").value("BUSINESS"))
                .andExpect(jsonPath("$.price").value(295))
                .andExpect(jsonPath("$.state.id").value(7));
    }
}
