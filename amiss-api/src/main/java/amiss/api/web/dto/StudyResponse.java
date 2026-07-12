package amiss.api.web.dto;

/**
 * A completed study session (KAN-54). {@code degreeCompleted} is {@code null} unless this
 * session just finished a degree, in which case {@code studiesDone} resets to 0 and {@code
 * studiesRemaining} is 0.
 */
public record StudyResponse(int studiesDone, int studiesRemaining, String degreeCompleted,
        int minutesCharged, SaveStateDto state) {
}
