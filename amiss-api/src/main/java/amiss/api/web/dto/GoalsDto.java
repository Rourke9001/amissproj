package amiss.api.web.dto;

/**
 * The four win-condition goals shown on {@code MainGameGUI}: cash /1000, happiness /200,
 * work experience /200, education /8.
 */
public record GoalsDto(GoalDto cash, GoalDto happiness, GoalDto workExperience, GoalDto education) {
}
