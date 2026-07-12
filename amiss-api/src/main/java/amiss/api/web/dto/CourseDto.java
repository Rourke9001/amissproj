package amiss.api.web.dto;

/**
 * One row of a save's {@code GET .../courses} board (KAN-54): a degree, this save's standing
 * against it ({@code status} one of {@code EARNED}/{@code AVAILABLE}/{@code LOCKED}), and its
 * prerequisite's name when it has one gating it.
 */
public record CourseDto(int id, String name, String status, String prereqName, boolean enrolled, int studiesDone) {
}
