package amiss.api.web.dto;

/** A successful clothing purchase: the new clothing level, plus the fresh state. */
public record ClothesResponse(String item, int price, int clothingLevel, SaveStateDto state) {
}
