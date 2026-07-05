package amiss.api.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import amiss.api.config.GameServicesFactory;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.PlayerStateDto;
import amiss.application.config.ActionCosts;
import amiss.application.service.EatOutcome;
import amiss.application.service.FoodService;
import amiss.application.service.GameServices;
import amiss.application.service.PurchaseOutcome;
import amiss.application.service.StatsService;
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

/** The KAN-32 food endpoints: catalog, eating, groceries and clothes. */
@WebMvcTest(FoodController.class)
@Import(GlobalExceptionHandler.class)
class FoodControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GameServicesFactory factory;

    @MockitoBean
    private PlayerStateAssembler assembler;

    private static PlayerStateDto dto() {
        return new PlayerStateDto("bob", 3, 3960, "66h", false, 70, 0, 0, false,
                1, 1, null, null, null,
                new LocationDto("MONOLITH_BURGERS", "Monolith Burgers", 3, 1, 4));
    }

    private static MockHttpServletRequestBuilder postBody(String path, String field, String value) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + field + "\":\"" + value + "\"}");
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

    // ---- GET /api/food ---------------------------------------------------------

    @Test
    void catalog_returnsTheMenuAndPacks() throws Exception {
        mvc.perform(get("/api/food"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menu.length()").value(6))
                .andExpect(jsonPath("$.menu[0].id").value("BURGER"))
                .andExpect(jsonPath("$.menu[0].name").value("Burger"))
                .andExpect(jsonPath("$.menu[0].price").value(32))
                .andExpect(jsonPath("$.packs.length()").value(4))
                .andExpect(jsonPath("$.packs[0].id").value("ONE_WEEK"))
                .andExpect(jsonPath("$.packs[0].price").value(25))
                .andExpect(jsonPath("$.packs[0].weeks").value(1));
    }

    // ---- POST /api/players/{u}/eat --------------------------------------------

    @Test
    void eat_unknownItemIsA400Problem() throws Exception {
        mvc.perform(postBody("/api/players/bob/eat", "item", "CAVIAR"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-item"));
    }

    @Test
    void eat_wrongLocationIsA409Problem() throws Exception {
        mockServicesAt(Location.PAWN_SHOP);

        mvc.perform(postBody("/api/players/bob/eat", "item", "BURGER"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void eat_insufficientTimeIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.MONOLITH_BURGERS);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.eat(32)).thenReturn(new EatOutcome(EatOutcome.Status.INSUFFICIENT_TIME, 50, -1, 32));

        mvc.perform(postBody("/api/players/bob/eat", "item", "BURGER"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }

    @Test
    void eat_insufficientCashIsA200WithAteFalse() throws Exception {
        GameServices services = mockServicesAt(Location.MONOLITH_BURGERS);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.eat(32)).thenReturn(new EatOutcome(EatOutcome.Status.INSUFFICIENT_CASH, 3900, 10, 32));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(postBody("/api/players/bob/eat", "item", "BURGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item").value("BURGER"))
                .andExpect(jsonPath("$.price").value(32))
                .andExpect(jsonPath("$.ate").value(false))
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_CASH"))
                .andExpect(jsonPath("$.minutesCharged").value(60));
    }

    @Test
    void eat_okReturnsAteTrue() throws Exception {
        GameServices services = mockServicesAt(Location.MONOLITH_BURGERS);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.eat(32)).thenReturn(new EatOutcome(EatOutcome.Status.OK, 3900, 38, 32));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(postBody("/api/players/bob/eat", "item", "BURGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ate").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.minutesCharged").value(60))
                .andExpect(jsonPath("$.state.username").value("bob"));
    }

    // ---- POST /api/players/{u}/groceries ---------------------------------------

    @Test
    void groceries_unknownItemIsA400Problem() throws Exception {
        mvc.perform(postBody("/api/players/bob/groceries", "pack", "FIFTY_WEEKS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-item"));
    }

    @Test
    void groceries_wrongLocationIsA409Problem() throws Exception {
        mockServicesAt(Location.PAWN_SHOP);

        mvc.perform(postBody("/api/players/bob/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void groceries_weekOverIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.BLACKS_MARKET);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.buyGroceries(48, 2)).thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.WEEK_OVER, 0, 70));

        mvc.perform(postBody("/api/players/bob/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:week-over"));
    }

    @Test
    void groceries_insufficientCashIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.BLACKS_MARKET);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.buyGroceries(48, 2)).thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, 3900, 10));

        mvc.perform(postBody("/api/players/bob/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void groceries_insufficientTimeIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.BLACKS_MARKET);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.buyGroceries(48, 2)).thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_TIME, 50, 70));

        mvc.perform(postBody("/api/players/bob/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-time"));
    }

    @Test
    void groceries_okReturnsWeeksAddedAndFoodWeeks() throws Exception {
        GameServices services = mockServicesAt(Location.BLACKS_MARKET);
        StatsService stats = mock(StatsService.class);
        FoodService food = mock(FoodService.class);
        when(services.stats()).thenReturn(stats);
        when(services.food()).thenReturn(food);
        when(stats.buyGroceries(48, 2)).thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.OK, 3900, 22));
        when(food.getFood()).thenReturn(2);
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(postBody("/api/players/bob/groceries", "pack", "TWO_WEEKS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pack").value("TWO_WEEKS"))
                .andExpect(jsonPath("$.price").value(48))
                .andExpect(jsonPath("$.weeksAdded").value(2))
                .andExpect(jsonPath("$.foodWeeks").value(2))
                .andExpect(jsonPath("$.state.username").value("bob"));
    }

    // ---- POST /api/players/{u}/clothes -----------------------------------------

    @Test
    void clothes_unknownItemIsA400Problem() throws Exception {
        mvc.perform(postBody("/api/players/bob/clothes", "item", "TUXEDO"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:unknown-item"));
    }

    @Test
    void clothes_wrongLocationIsA409Problem() throws Exception {
        mockServicesAt(Location.PAWN_SHOP);

        mvc.perform(postBody("/api/players/bob/clothes", "item", "SUIT"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:wrong-location"));
    }

    @Test
    void clothes_insufficientCashIsA409Problem() throws Exception {
        GameServices services = mockServicesAt(Location.QT_CLOTHING);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.buyClothes(3, 55)).thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, 3900, 10));

        mvc.perform(postBody("/api/players/bob/clothes", "item", "SUIT"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:amiss:insufficient-funds"));
    }

    @Test
    void clothes_okReturnsClothingLevelAndFreshState() throws Exception {
        GameServices services = mockServicesAt(Location.QT_CLOTHING);
        StatsService stats = mock(StatsService.class);
        when(services.stats()).thenReturn(stats);
        when(stats.buyClothes(3, 55)).thenReturn(new PurchaseOutcome(PurchaseOutcome.Status.OK, 3900, 15));
        when(assembler.assemble("bob", services)).thenReturn(dto());

        mvc.perform(postBody("/api/players/bob/clothes", "item", "SUIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item").value("SUIT"))
                .andExpect(jsonPath("$.price").value(55))
                .andExpect(jsonPath("$.clothingLevel").value(3))
                .andExpect(jsonPath("$.state.username").value("bob"));
    }
}
