package amiss.api.web.dto;

/**
 * A completed study session (KAN-54). {@code degreeCompleted} is {@code null} unless this
 * session just finished a degree, in which case {@code studiesDone} resets to 0 and {@code
 * studiesRemaining} is 0. {@code studiesRequired} is the per-save session count needed to
 * graduate — 10, minus 1 for owning a Computer, minus 1 more for owning all three Books,
 * floor 8 (KAN-23 extra credit).
 */
public record StudyResponse(int studiesDone, int studiesRemaining, int studiesRequired,
        String degreeCompleted, int minutesCharged, SaveStateDto state) {
}
