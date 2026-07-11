package amiss.api.web;

import amiss.api.web.dto.CurrentCourseDto;
import amiss.api.web.dto.GoalDto;
import amiss.api.web.dto.GoalsDto;
import amiss.api.web.dto.JobDto;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.SaveStateDto;
import amiss.application.port.JobCatalog;
import amiss.application.service.TimeService;
import amiss.application.service.save.CourseService;
import amiss.application.service.save.GoalService;
import amiss.application.service.save.SaveGameServices;
import amiss.domain.board.Board;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds the wire representation of a save (KAN-54: rewritten from the per-username
 * successor over {@link SaveGameServices}). Board data comes exclusively from {@code
 * domain.board.Board}; a saved position that is not a stop (e.g. from a pre-ring save) is
 * reported as home. Goals are per-save (no hardcoded targets); education is degrees earned
 * plus the course in progress, never a linear level. {@code experience} and {@code
 * dependability} are hidden stats and never appear on the wire.
 */
@Component
public class PlayerStateAssembler {

    private static final String UNEMPLOYED = "Unemployed";

    private final JobCatalog jobs;
    private final Board board = new Board();

    public PlayerStateAssembler(JobCatalog jobs) {
        this.jobs = jobs;
    }

    public SaveStateDto assemble(SaveGameServices services, SaveState save) {
        int row = save.xpos();
        int col = save.ypos();
        if (!board.isStop(row, col)) {
            int[] home = board.cellOf(0);
            row = home[0];
            col = home[1];
        }
        Location location = board.locationAt(row, col);

        List<CourseService.CourseView> courses = services.courses().courses(save);
        List<String> degreesEarned = courses.stream()
                .filter(c -> c.status() == CourseService.CourseStatus.EARNED)
                .map(c -> c.degree().name())
                .toList();
        CurrentCourseDto currentCourse = courses.stream()
                .filter(CourseService.CourseView::enrolled)
                .findFirst()
                .map(c -> new CurrentCourseDto(c.degree().id(), c.degree().name(), c.studiesDone()))
                .orElse(null);

        return new SaveStateDto(
                save.id(),
                save.label(),
                save.round(),
                save.timeMinutes(),
                TimeService.format(save.timeMinutes()),
                save.weekOver(),
                save.cash(),
                save.bank(),
                save.debt(),
                save.rent() == 1,
                save.eat(),
                save.clothing(),
                jobFor(save),
                new LocationDto(location.name(), location.displayName(), board.ringIndex(row, col), row, col),
                degreesEarned,
                currentCourse,
                goalsFor(services, save),
                save.won());
    }

    private JobDto jobFor(SaveState save) {
        if (!save.employed()) {
            return new JobDto(UNEMPLOYED, null, null);
        }
        return jobs.byId(save.jobId())
                .map(job -> new JobDto(job.name(), job.wage(), job.location()))
                .orElse(new JobDto(UNEMPLOYED, null, null));
    }

    private static GoalsDto goalsFor(SaveGameServices services, SaveState save) {
        GoalService.GoalsProgress progress = services.goals().progress(save);
        return new GoalsDto(
                new GoalDto(progress.wealth().current(), progress.wealth().target()),
                new GoalDto(progress.happiness().current(), progress.happiness().target()),
                new GoalDto(progress.education().current(), progress.education().target()),
                new GoalDto(progress.career().current(), progress.career().target()));
    }
}
