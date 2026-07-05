package amiss.api.web.dto;

/** Body of {@code POST .../clothes}: the {@code ClothingItem} id, e.g. {@code "SUIT"}. */
public record ClothesRequest(String item) {
}
