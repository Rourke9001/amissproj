package amiss.api.web;

import amiss.api.config.GameServicesFactory;
import amiss.api.error.AlreadyEnrolledException;
import amiss.api.error.EducationCompleteException;
import amiss.api.error.InsufficientFundsException;
import amiss.api.error.InsufficientTimeException;
import amiss.api.error.NotEnrolledException;
import amiss.api.error.WeekOverException;
import amiss.api.web.dto.CoursesDto;
import amiss.api.web.dto.DegreeDto;
import amiss.api.web.dto.EnrollResponse;
import amiss.api.web.dto.StudyResponse;
import amiss.application.config.ActionCosts;
import amiss.application.port.PersistenceFailureException;
import amiss.application.service.EnrollOutcome;
import amiss.application.service.GameServices;
import amiss.application.service.StudyOutcome;
import amiss.application.service.UniversityService;
import amiss.domain.board.Location;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Hi-Tech U catalog, enrolling and studying (KAN-32). Both mutating actions require
 * standing at {@link Location#HI_TECH_U}.
 */
@RestController
public class UniversityController {

    private final GameServicesFactory factory;
    private final PlayerStateAssembler assembler;
    private final ActionCosts costs;

    public UniversityController(GameServicesFactory factory, PlayerStateAssembler assembler, ActionCosts costs) {
        this.factory = factory;
        this.assembler = assembler;
        this.costs = costs;
    }

    @GetMapping("/api/courses")
    public CoursesDto courses() {
        List<DegreeDto> degrees = new ArrayList<>(UniversityService.DEGREES.length);
        for (int i = 0; i < UniversityService.DEGREES.length; i++) {
            degrees.add(new DegreeDto(i + 1, UniversityService.DEGREES[i]));
        }
        return new CoursesDto(degrees, UniversityService.ENROLL_FEE, UniversityService.STUDIES_PER_DEGREE,
                costs.studyMinutes());
    }

    @PostMapping("/api/players/{username}/enroll")
    public EnrollResponse enroll(@PathVariable String username) {
        GameServices services = factory.forPlayer(username);
        LocationGuard.requireAt(services, Location.HI_TECH_U);

        EnrollOutcome outcome = services.university().enroll();
        switch (outcome.status()) {
            case ALREADY_ENROLLED:
                throw new AlreadyEnrolledException(username);
            case EDUCATION_COMPLETE:
                throw new EducationCompleteException(username);
            case INSUFFICIENT_CASH:
                throw new InsufficientFundsException(username);
            case WEEK_OVER:
                throw new WeekOverException(username);
            case FAILED:
                throw new PersistenceFailureException("Enrollment failed for '" + username + "'", null);
            default:
                return new EnrollResponse(UniversityService.ENROLL_FEE, assembler.assemble(username, services));
        }
    }

    @PostMapping("/api/players/{username}/study")
    public StudyResponse study(@PathVariable String username) {
        GameServices services = factory.forPlayer(username);
        LocationGuard.requireAt(services, Location.HI_TECH_U);

        StudyOutcome outcome = services.university().study();
        switch (outcome.status()) {
            case NOT_ENROLLED:
                throw new NotEnrolledException(username);
            case EDUCATION_COMPLETE:
                throw new EducationCompleteException(username);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(username);
            case WEEK_OVER:
                throw new WeekOverException(username);
            case FAILED:
                throw new PersistenceFailureException("Study session failed for '" + username + "'", null);
            default:
                int studiesRemaining = outcome.status() == StudyOutcome.Status.DEGREE_COMPLETED
                        ? 0
                        : UniversityService.STUDIES_PER_DEGREE - outcome.progress() + 1;
                return new StudyResponse(outcome.progress(), studiesRemaining, outcome.degreeCompleted(),
                        outcome.educationLevel(), services.costs().studyMinutes(), assembler.assemble(username, services));
        }
    }
}
