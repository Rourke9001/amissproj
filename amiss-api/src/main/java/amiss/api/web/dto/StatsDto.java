package amiss.api.web.dto;

/** The player's progression stats: education level/progress, happiness and work experience. */
public record StatsDto(int education, int educationProgress, int happiness, int workExperience) {
}
