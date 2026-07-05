package amiss.api.web.dto;

/** One item on the Monolith Burgers menu ({@code id} is the {@code FastFoodItem} enum name). */
public record MenuItemDto(String id, String name, int price) {
}
