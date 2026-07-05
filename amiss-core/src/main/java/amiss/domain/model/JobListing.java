package amiss.domain.model;

/** One row of the {@code tbljobs} catalog: a job the game offers and its requirements. */
public record JobListing(String name, int requiredEducation, int hourlyWage, String location, int requiredClothing) {
}
