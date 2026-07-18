package amiss.api.web;

import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InsufficientTimeException;
import amiss.api.error.UnknownItemException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.ClothesRequest;
import amiss.api.web.dto.ClothesResponse;
import amiss.api.web.dto.ClothingItemDto;
import amiss.api.web.dto.EatRequest;
import amiss.api.web.dto.EatResponse;
import amiss.api.web.dto.FoodCatalogDto;
import amiss.api.web.dto.FoodPackDto;
import amiss.api.web.dto.GroceriesRequest;
import amiss.api.web.dto.GroceriesResponse;
import amiss.api.web.dto.MenuItemDto;
import amiss.application.config.ActionCosts;
import amiss.application.service.save.EatOutcome;
import amiss.application.service.save.EconomyService;
import amiss.application.service.save.PurchaseOutcome;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.board.Location;
import amiss.domain.model.ClothingItem;
import amiss.domain.model.FastFoodItem;
import amiss.domain.model.FoodPack;
import amiss.domain.model.SaveState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The food/clothing catalogs, eating, buying groceries and buying clothes (KAN-54: cut over
 * to {@code /api/saves/{saveId}}). Each mutating action requires standing at the item's
 * building; the catalogs are save-scoped so displayed prices follow the save's economy (KAN-48).
 */
@RestController
public class FoodController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;
    private final ActionCosts costs;

    public FoodController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler,
            ActionCosts costs) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
        this.costs = costs;
    }

    @GetMapping("/api/saves/{saveId}/food")
    public FoodCatalogDto catalog(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        EconomyService economy = services.economy();
        List<MenuItemDto> menu = new ArrayList<>(FastFoodItem.values().length);
        for (FastFoodItem item : FastFoodItem.values()) {
            menu.add(new MenuItemDto(item.name(), item.displayName(),
                    economy.price(item.price(), save)));
        }
        List<FoodPackDto> packs = new ArrayList<>(FoodPack.values().length);
        for (FoodPack pack : FoodPack.values()) {
            packs.add(new FoodPackDto(pack.name(), pack.displayName(),
                    economy.price(pack.price(), save), pack.weeks()));
        }
        return new FoodCatalogDto(menu, packs);
    }

    @GetMapping("/api/saves/{saveId}/clothes")
    public List<ClothingItemDto> clothesCatalog(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        EconomyService economy = services.economy();
        List<ClothingItemDto> items = new ArrayList<>(ClothingItem.values().length);
        for (ClothingItem item : ClothingItem.values()) {
            items.add(new ClothingItemDto(item.name(), item.displayName(),
                    economy.price(item.price(), save), item.level(), item.weeks()));
        }
        return items;
    }

    @PostMapping("/api/saves/{saveId}/eat")
    public EatResponse eat(@PathVariable long saveId, Authentication authentication, @RequestBody EatRequest request) {
        FastFoodItem item = parseFastFoodItem(request.item());
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.MONOLITH_BURGERS);

        EatOutcome outcome = services.shop().eat(save, item);
        switch (outcome.status()) {
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            case INSUFFICIENT_CASH:
                return new EatResponse(item.name(), outcome.price(), false, "INSUFFICIENT_CASH",
                        costs.eatMinutes(), assembler.assemble(services, save));
            default:
                return new EatResponse(item.name(), outcome.price(), true, null,
                        costs.eatMinutes(), assembler.assemble(services, save));
        }
    }

    @PostMapping("/api/saves/{saveId}/groceries")
    public GroceriesResponse groceries(@PathVariable long saveId, Authentication authentication,
            @RequestBody GroceriesRequest request) {
        FoodPack pack = parseFoodPack(request.pack());
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.BLACKS_MARKET);
        int freshFoodWeeksBefore = save.eat();

        PurchaseOutcome outcome = services.shop().buyGroceries(save, pack);
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(saveId);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            default:
                return new GroceriesResponse(pack.name(), outcome.pricePaid(),
                        save.eat() - freshFoodWeeksBefore, save.eat(), assembler.assemble(services, save));
        }
    }

    @PostMapping("/api/saves/{saveId}/clothes")
    public ClothesResponse clothes(@PathVariable long saveId, Authentication authentication,
            @RequestBody ClothesRequest request) {
        ClothingItem item = parseClothingItem(request.item());
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.QT_CLOTHING);

        PurchaseOutcome outcome = services.shop().buyClothes(save, item);
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(saveId);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            default:
                return new ClothesResponse(item.name(), outcome.pricePaid(), assembler.assemble(services, save));
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
