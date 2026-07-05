package amiss.api.web.dto;

/** One grocery pack at Black's Market ({@code id} is the {@code FoodPack} enum name). */
public record FoodPackDto(String id, String name, int price, int weeks) {
}
