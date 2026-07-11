package amiss.api.web.dto;

/**
 * One row of the {@code GET /api/jobs} catalog (KAN-54). Deliberately just {@code
 * id/name/location/wage} — every requirement (education/experience/dependability/clothing)
 * is hidden pre-apply, so no requirement field of any kind belongs on this wire shape.
 */
public record JobListingDto(int id, String name, String location, int wage) {
}
