package amiss.api.web.dto;

/** One row of the appliance catalog (KAN-23): id, economy-adjusted price, owning store, owned flag. */
public record ApplianceDto(String id, int price, String store, boolean owned) {
}
