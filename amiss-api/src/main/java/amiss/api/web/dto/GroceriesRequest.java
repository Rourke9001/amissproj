package amiss.api.web.dto;

/** Body of {@code POST .../groceries}: the {@code FoodPack} id, e.g. {@code "TWO_WEEKS"}. */
public record GroceriesRequest(String pack) {
}
