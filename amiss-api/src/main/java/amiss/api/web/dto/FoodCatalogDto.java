package amiss.api.web.dto;

import java.util.List;

/** The static food catalog: the fast-food menu and the grocery packs. */
public record FoodCatalogDto(List<MenuItemDto> menu, List<FoodPackDto> packs) {
}
