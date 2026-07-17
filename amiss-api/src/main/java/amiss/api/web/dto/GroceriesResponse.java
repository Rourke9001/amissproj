package amiss.api.web.dto;

/**
 * A successful grocery purchase. {@code weeksAdded} is the real, capacity-aware delta in
 * stored fresh food (clamped by fridge/freezer ownership), not the pack's nominal
 * {@code weeks()} size; {@code foodWeeks} is the resulting stored-food total.
 */
public record GroceriesResponse(String pack, int price, int weeksAdded, int foodWeeks, SaveStateDto state) {
}
