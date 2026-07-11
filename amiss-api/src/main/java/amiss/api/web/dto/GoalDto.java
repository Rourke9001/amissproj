package amiss.api.web.dto;

/** One win-goal's current progress against its per-save target. */
public record GoalDto(int current, int target, boolean met) {

    public GoalDto(int current, int target) {
        this(current, target, current >= target);
    }
}
