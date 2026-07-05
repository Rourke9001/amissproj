package amiss.api.web.dto;

/** One row of the {@code GET /api/jobs} catalog. */
public record JobListingDto(String name, int requiredEducation, int hourlyWage, String location, int requiredClothing) {
}
