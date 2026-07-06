package amiss.api.web.dto;

/** Body of {@code POST .../eat}: the {@code FastFoodItem} id, e.g. {@code "BURGER"}. */
public record EatRequest(String item) {
}
