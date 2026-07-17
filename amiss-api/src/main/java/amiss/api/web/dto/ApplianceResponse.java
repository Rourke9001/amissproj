package amiss.api.web.dto;

/** A successful appliance purchase. */
public record ApplianceResponse(String item, int price, SaveStateDto state) {
}
