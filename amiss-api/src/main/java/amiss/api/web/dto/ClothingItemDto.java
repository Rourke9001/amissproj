package amiss.api.web.dto;

/** One QT Clothing stock item on the wire ({@code GET /api/clothes}, KAN-23). */
public record ClothingItemDto(String id, String name, int price, int level, int weeks) {
}
