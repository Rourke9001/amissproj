package amiss.api.web.dto;

/** A successful grocery purchase: weeks of food added, plus the new stored-food total. */
public record GroceriesResponse(String pack, int price, int weeksAdded, int foodWeeks, PlayerStateDto state) {
}
