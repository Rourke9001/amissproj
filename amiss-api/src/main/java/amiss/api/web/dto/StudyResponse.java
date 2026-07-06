package amiss.api.web.dto;

/**
 * A completed study session. {@code degreeCompleted} is {@code null} unless this session just
 * finished a degree, in which case {@code progress} is 0 and {@code educationLevel} reflects
 * the newly completed level.
 */
public record StudyResponse(int progress, int studiesRemaining, String degreeCompleted,
        int educationLevel, int minutesCharged, PlayerStateDto state) {
}
