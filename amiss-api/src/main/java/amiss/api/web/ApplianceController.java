package amiss.api.web;

import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InsufficientTimeException;
import amiss.api.error.UnknownItemException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.ApplianceDto;
import amiss.api.web.dto.ApplianceRequest;
import amiss.api.web.dto.ApplianceResponse;
import amiss.application.service.save.EconomyService;
import amiss.application.service.save.PurchaseOutcome;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.model.ApplianceItem;
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
 * The minimal appliance/book catalog (KAN-23): Fridge/Freezer/Computer at Socket City,
 * the three Books at Z-Mart (per {@link ApplianceItem#store()}) — no browsing beyond a
 * flat list, no break/repair (KAN-58). Buying requires standing at the item's store.
 */
@RestController
public class ApplianceController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;

    public ApplianceController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
    }

    @GetMapping("/api/saves/{saveId}/appliances")
    public List<ApplianceDto> catalog(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        EconomyService economy = services.economy();
        List<ApplianceDto> items = new ArrayList<>(ApplianceItem.values().length);
        for (ApplianceItem item : ApplianceItem.values()) {
            items.add(new ApplianceDto(item.name(), economy.price(item.basePrice(), save),
                    item.store().name(), save.owns(item)));
        }
        return items;
    }

    @PostMapping("/api/saves/{saveId}/appliances")
    public ApplianceResponse buy(@PathVariable long saveId, Authentication authentication,
            @RequestBody ApplianceRequest request) {
        ApplianceItem item = parseApplianceItem(request.item());
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, item.store());

        PurchaseOutcome outcome = services.appliances().buy(save, item);
        switch (outcome.status()) {
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(saveId);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(saveId);
            default:
                return new ApplianceResponse(item.name(), outcome.pricePaid(), assembler.assemble(services, save));
        }
    }

    private static ApplianceItem parseApplianceItem(String raw) {
        if (raw == null) {
            throw new UnknownItemException(raw);
        }
        try {
            return ApplianceItem.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownItemException(raw);
        }
    }
}
