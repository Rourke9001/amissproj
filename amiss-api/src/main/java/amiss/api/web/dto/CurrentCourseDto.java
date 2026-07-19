package amiss.api.web.dto;

/**
 * The degree a save is currently studying, how many sessions are done, and how many are
 * required to graduate it — base 10, reduced by extra credit (KAN-23: Computer and/or all
 * three Books owned), floor 8.
 */
public record CurrentCourseDto(int id, String name, int studiesDone, int studiesRequired) {
}
