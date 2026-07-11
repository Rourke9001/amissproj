package amiss.api.web.dto;

/**
 * Body of {@code POST /api/saves}. Either {@code goals} names all four targets (each
 * 10-100) or {@code random} is {@code true} and every target is rolled instead.
 */
public record CreateSaveRequest(String label, GoalTargets goals, Boolean random) {

    /** The four win-goal targets a new save starts with; each must be 10-100 inclusive. */
    public record GoalTargets(Integer wealth, Integer happiness, Integer education, Integer career) {
    }
}
