package amiss.domain.model;

/**
 * One row of the {@code tbldegrees} catalog (KAN-53): a Hi-Tech U course/degree and
 * the single degree that must be earned first ({@code prereqDegreeId} null = open
 * from the start).
 */
public record DegreeSpec(int id, String name, Integer prereqDegreeId) {
}
