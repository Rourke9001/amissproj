package amiss.api.web;

import amiss.api.error.AlreadyEnrolledException;
import amiss.api.error.DegreeAlreadyEarnedException;
import amiss.api.error.DegreeLockedException;
import amiss.api.error.InsufficientFundsException;
import amiss.api.error.NotEnrolledException;
import amiss.api.error.UnknownDegreeException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.CourseDto;
import amiss.api.web.dto.EnrollResponse;
import amiss.api.web.dto.StudyResponse;
import amiss.application.config.ActionCosts;
import amiss.application.service.save.CourseService;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hi-Tech U's per-save course board, enrolling and studying (KAN-54: cut over from the
 * static {@code /api/courses} catalog to the save-scoped 11-degree tree). Both mutating
 * actions require standing at {@link Location#HI_TECH_U}.
 */
@RestController
public class UniversityController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;
    private final ActionCosts costs;

    public UniversityController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler,
            ActionCosts costs) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
        this.costs = costs;
    }

    @GetMapping("/api/saves/{saveId}/courses")
    public List<CourseDto> courses(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        List<CourseService.CourseView> board = services.courses().courses(save);

        Map<Integer, String> namesById = new HashMap<>();
        for (CourseService.CourseView course : board) {
            namesById.put(course.degree().id(), course.degree().name());
        }

        return board.stream()
                .map(course -> new CourseDto(
                        course.degree().id(),
                        course.degree().name(),
                        course.status().name(),
                        course.degree().prereqDegreeId() == null ? null : namesById.get(course.degree().prereqDegreeId()),
                        course.enrolled(),
                        course.studiesDone()))
                .toList();
    }

    @PostMapping("/api/saves/{saveId}/courses/{degreeId}/enroll")
    public EnrollResponse enroll(@PathVariable long saveId, @PathVariable int degreeId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.HI_TECH_U);

        CourseService.EnrollResult outcome = services.courses().enroll(save, degreeId);
        switch (outcome.status()) {
            case UNKNOWN_DEGREE:
                throw new UnknownDegreeException(degreeId);
            case ALREADY_EARNED:
                throw new DegreeAlreadyEarnedException(degreeId);
            case LOCKED:
                throw new DegreeLockedException(degreeId);
            case ALREADY_ENROLLED:
                throw new AlreadyEnrolledException(saveId);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(saveId);
            default:
                return new EnrollResponse(CourseService.ENROLL_FEE, assembler.assemble(services, save));
        }
    }

    @PostMapping("/api/saves/{saveId}/courses/study")
    public StudyResponse study(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.HI_TECH_U);

        CourseService.StudyResult outcome = services.courses().study(save);
        switch (outcome.status()) {
            case NOT_ENROLLED:
                throw new NotEnrolledException(saveId);
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            default:
                int studiesRemaining = outcome.status() == CourseService.StudyResult.Status.GRADUATED
                        ? 0
                        : CourseService.STUDIES_PER_DEGREE - outcome.studiesDone();
                return new StudyResponse(outcome.studiesDone(), studiesRemaining, outcome.degreeCompleted(),
                        costs.studyMinutes(), assembler.assemble(services, save));
        }
    }
}
