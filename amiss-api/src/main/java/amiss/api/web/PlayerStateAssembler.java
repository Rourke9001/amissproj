package amiss.api.web;

import amiss.api.web.dto.GoalDto;
import amiss.api.web.dto.GoalsDto;
import amiss.api.web.dto.JobDto;
import amiss.api.web.dto.LocationDto;
import amiss.api.web.dto.PlayerStateDto;
import amiss.api.web.dto.StatsDto;
import amiss.application.service.EducationService;
import amiss.application.service.GameServices;
import amiss.application.service.JobService;
import amiss.application.service.StatsService;
import amiss.application.service.TimeService;
import amiss.application.service.TimeSpend;
import amiss.domain.board.Board;
import amiss.domain.board.Location;
import amiss.domain.validation.Validation;
import org.springframework.stereotype.Component;

/**
 * Builds the wire representation of a player from their {@link GameServices}. Board data
 * comes exclusively from {@code domain.board.Board} — never from the Swing-side
 * {@code OpenLocation}, whose coordinate map is stale. A saved position that is not a stop
 * (e.g. from a pre-ring save) is reported as home; gameplay actions persist real positions.
 */
@Component
public class PlayerStateAssembler {

    /** Goal targets shown on {@code MainGameGUI}'s progress bars. */
    private static final int CASH_GOAL = 1000;
    private static final int HAPPINESS_GOAL = 200;
    private static final int WORK_EXPERIENCE_GOAL = 200;
    private static final int EDUCATION_GOAL = 8;

    private static final String UNEMPLOYED = "Unemployed";

    private final Board board = new Board();

    public PlayerStateDto assemble(String username, GameServices services) {
        TimeService time = services.time();
        StatsService stats = services.stats();
        JobService jobs = services.jobs();
        EducationService education = services.education();

        TimeSpend clock = time.spendMinutes(0);
        int row = time.getX();
        int col = time.getY();
        if (!board.isStop(row, col)) {
            int[] home = board.cellOf(0);
            row = home[0];
            col = home[1];
        }
        Location location = board.locationAt(row, col);

        String jobName = jobs.getJob();
        boolean employed = !UNEMPLOYED.equals(jobName);
        JobDto job = new JobDto(jobName,
                employed ? jobs.getEarnings() : null,
                employed ? jobs.getLocation() : null);

        int happiness = Validation.parseIntOrDefault(stats.getHappiness(), 0);
        int workExperience = Validation.parseIntOrDefault(stats.getWork(), 0);
        int educationLevel = education.getEducation();
        int cash = stats.getCash();

        StatsDto statsDto = new StatsDto(educationLevel, education.getProg(), happiness, workExperience);
        GoalsDto goals = new GoalsDto(
                new GoalDto(cash, CASH_GOAL),
                new GoalDto(happiness, HAPPINESS_GOAL),
                new GoalDto(workExperience, WORK_EXPERIENCE_GOAL),
                new GoalDto(educationLevel, EDUCATION_GOAL));

        return new PlayerStateDto(
                username,
                Integer.parseInt(time.getRound()),
                clock.remainingMinutes(),
                TimeService.format(clock.remainingMinutes()),
                clock.weekOver(),
                cash,
                services.bank().balance(),
                stats.getDebt(),
                stats.getRent() == 1,
                services.food().getFood(),
                jobs.getClothingLevel(),
                job,
                statsDto,
                goals,
                new LocationDto(location.name(), location.displayName(),
                        board.ringIndex(row, col), row, col));
    }
}
