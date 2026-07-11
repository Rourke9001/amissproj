package amiss.api.web.dto;

/** The degree a save is currently studying, and how many of the 10 sessions are done. */
public record CurrentCourseDto(int id, String name, int studiesDone) {
}
