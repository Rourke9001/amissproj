package amiss.api.web.dto;

/** One goal's current progress against its target (the {@code MainGameGUI} goal bars). */
public record GoalDto(int current, int target) {
}
