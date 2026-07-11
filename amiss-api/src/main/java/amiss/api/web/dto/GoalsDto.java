package amiss.api.web.dto;

/**
 * The four per-save win-condition goals (KAN-53/KAN-54): {@code wealthStat = (cash+bank)/100},
 * {@code happinessStat = happiness}, {@code educationStat = 1 + 9*degrees}, {@code careerStat =
 * floor(1.25*dependability)} (0 while unemployed). Winning is all four {@code met} at once.
 */
public record GoalsDto(GoalDto wealth, GoalDto happiness, GoalDto education, GoalDto career) {
}
