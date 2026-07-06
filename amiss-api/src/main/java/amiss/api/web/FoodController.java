package amiss.api.web;

import amiss.api.config.GameServicesFactory;
import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InsufficientTimeException;
import amiss.api.error.UnknownItemException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.ClothesRequest;
import amiss.api.web.dto.ClothesResponse;
import amiss.api.web.dto.EatRequest;
import amiss.api.web.dto.EatResponse;
import amiss.api.web.dto.FoodCatalogDto;
import amiss.api.web.dto.FoodPackDto;
import amiss.api.web.dto.GroceriesRequest;
import amiss.api.web.dto.GroceriesResponse;
import amiss.api.web.dto.MenuItemDto;
import amiss.application.port.PersistenceFailureException;
import amiss.application.service.EatOutcome;
import amiss.application.service.GameServices;
import amiss.application.service.PurchaseOutcome;
import amiss.domain.board.Location;
import amiss.domain.model.ClothingItem;
import amiss.domain.model.FastFoodItem;
import amiss.domain.model.FoodPack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The food/clothing catalogs, eating, buying groceries and buying clothes (KAN-32; the clothes
 * endpoint is the scope addition noted on KAN-32). Each mutating action requires standing at
 * the item's building.
 */
@RestController
public class FoodController {

    private final GameServicesFactory factory;
    private final PlayerStateAssembler assembler;

    public FoodController(GameServicesFactory factory, PlayerStateAssembler assembler) {
        this.factory = factory;
        this.assembler = assembler;
    }

    @GetMapping("/api/food")
    public FoodCatalogDto catalog() {
        List<MenuItemDto> menu = new ArrayList<>(FastFoodItem.values().length);
        for (FastFoodItem item : FastFoodItem.values()) {
            menu.add(new MenuItemDto(item.name(), item.displayName(), item.price()));
        }
        List<FoodPackDto> packs = new ArrayList<>(FoodPack.values().length);
        for (FoodPack pack : FoodPack.values()) {
            packs.add(new FoodPackDto(pack.name(), pack.displayName(), pack.price(), pack.weeks()));
        }
        return new FoodCatalogDto(menu, packs);
    }

    @PostMapping("/api/players/{username}/eat")
    public EatResponse eat(@PathVariable String username, @RequestBody EatRequest request) {
        FastFoodItem item = parseFastFoodItem(request.item());
        GameServices services = factory.forPlayer(username);
        LocationGuard.requireAt(services, Location.MONOLITH_BURGERS);

        EatOutcome outcome = services.stats().eat(item.price());
        switch (outcome.status()) {
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(username);
            case FAILED:
                throw new PersistenceFailureException("Eating failed for '" + username + "'");
            case INSUFFICIENT_CASH:
                return new EatResponse(item.name(), item.price(), false, "INSUFFICIENT_CASH",
                        services.costs().eatMinutes(), assembler.assemble(username, services));
            default:
                return new EatResponse(item.name(), item.price(), true, null,
                        services.costs().eatMinutes(), assembler.assemble(username, services));
        }
    }

    @PostMapping("/api/players/{username}/groceries")
    public GroceriesResponse groceries(@PathVariable String username, @RequestBody GroceriesRequest request) {
        FoodPack pack = parseFoodPack(request.pack());
        GameServices services = factory.forPlayer(username);
        LocationGuard.requireAt(services, Location.BLACKS_MARKET);

        PurchaseOutcome outcome = services.stats().buyGroceries(pack.price(), pack.weeks());
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(username);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(username);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(username);
            case FAILED:
                throw new PersistenceFailureException("Grocery purchase failed for '" + username + "'");
            default:
                return new GroceriesResponse(pack.name(), pack.price(), pack.weeks(),
                        services.food().getFood(), assembler.assemble(username, services));
        }
    }

    @PostMapping("/api/players/{username}/clothes")
    public ClothesResponse clothes(@PathVariable String username, @RequestBody ClothesRequest request) {
        ClothingItem item = parseClothingItem(request.item());
        GameServices services = factory.forPlayer(username);
        LocationGuard.requireAt(services, Location.QT_CLOTHING);

        PurchaseOutcome outcome = services.stats().buyClothes(item.level(), item.price());
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(username);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(username);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(username);
            case FAILED:
                throw new PersistenceFailureException("Clothing purchase failed for '" + username + "'");
            default:
                return new ClothesResponse(item.name(), item.price(), item.level(), assembler.assemble(username, services));
        }
    }

    private static FastFoodItem parseFastFoodItem(String raw) {
        if (raw == null) {
            throw new UnknownItemException(raw);
        }
        try {
            return FastFoodItem.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownItemException(raw);
        }
    }

    private static FoodPack parseFoodPack(String raw) {
        if (raw == null) {
            throw new UnknownItemException(raw);
        }
        try {
            return FoodPack.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownItemException(raw);
        }
    }

    private static ClothingItem parseClothingItem(String raw) {
        if (raw == null) {
            throw new UnknownItemException(raw);
        }
        try {
            return ClothingItem.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownItemException(raw);
        }
    }
}
