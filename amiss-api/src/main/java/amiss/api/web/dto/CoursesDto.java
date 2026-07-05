package amiss.api.web.dto;

import java.util.List;

/** The static Hi-Tech U catalog: degrees plus the game's enroll/study constants. */
public record CoursesDto(List<DegreeDto> degrees, int enrollFee, int studiesPerDegree, int studyMinutes) {
}
