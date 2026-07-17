package amiss.api.web.dto;

import java.util.List;

/**
 * A save's turn-relevant state on the wire (KAN-54), the save-scoped successor of {@code
 * PlayerStateDto}. {@code experience} and {@code dependability} are hidden stats and never
 * appear here; education is reported as earned degrees plus the course in progress rather
 * than a linear level/progress pair.
 *
 * @param location where the save stands on the 13-stop board ring; a stale saved cell (e.g.
 *                 from before the ring existed) is reported as home
 * @param degreesEarned names of every degree this save has earned, in catalog order
 * @param currentCourse the degree currently being studied, or {@code null} if not enrolled
 */
public record SaveStateDto(
        long id,
        String label,
        int round,
        int timeMinutes,
        String timeDisplay,
        boolean weekOver,
        int cash,
        int bank,
        int debt,
        boolean rentDue,
        int foodWeeks,
        boolean ateFastFoodLastTurn,
        int clothing,
        JobDto job,
        LocationDto location,
        List<String> degreesEarned,
        CurrentCourseDto currentCourse,
        GoalsDto goals,
        boolean won) {
}
