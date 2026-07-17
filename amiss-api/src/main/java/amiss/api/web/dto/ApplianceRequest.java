package amiss.api.web.dto;

/** Body of {@code POST .../appliances}: the {@code ApplianceItem} id, e.g. {@code "FRIDGE"}. */
public record ApplianceRequest(String item) {
}
