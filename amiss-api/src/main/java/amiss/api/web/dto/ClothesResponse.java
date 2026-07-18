package amiss.api.web.dto;

/** A successful clothing purchase. */
public record ClothesResponse(String item, int price, SaveStateDto state) {
}
