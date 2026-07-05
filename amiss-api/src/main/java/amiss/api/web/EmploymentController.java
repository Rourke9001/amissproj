package amiss.api.web;

import amiss.api.config.GameServicesFactory;
import amiss.api.error.InsufficientTimeException;
import amiss.api.error.NoJobException;
import amiss.api.error.PersistenceFailureException;
import amiss.api.error.UnderdressedException;
import amiss.api.error.UnknownJobException;
import amiss.api.error.WeekOverException;
import amiss.api.error.WrongLocationException;
import amiss.api.web.dto.ApplyRequest;
import amiss.api.web.dto.ApplyResponse;
import amiss.api.web.dto.JobListingDto;
import amiss.api.web.dto.WorkResponse;
import amiss.application.port.JobRepository;
import amiss.application.service.ApplyOutcome;
import amiss.application.service.GameServices;
import amiss.application.service.JobService;
import amiss.application.service.WorkOutcome;
import amiss.domain.board.Location;
import amiss.domain.model.JobListing;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The job catalog, applying and working (KAN-32). Applying is only reachable at the
 * {@link Location#EMPLOYMENT_OFFICE}; working requires standing at the job's own location,
 * which varies per job (read from {@code tbljobs.location}), so it is checked inline rather
 * than via {@link LocationGuard}.
 */
@RestController
public class EmploymentController {

    private static final String UNEMPLOYED = "Unemployed";

    private final GameServicesFactory factory;
    private final PlayerStateAssembler assembler;
    private final JobRepository jobs;

    public EmploymentController(GameServicesFactory factory, PlayerStateAssembler assembler, JobRepository jobs) {
        this.factory = factory;
        this.assembler = assembler;
        this.jobs = jobs;
    }

    @GetMapping("/api/jobs")
    public List<JobListingDto> jobs() {
        try {
            List<JobListing> listings = jobs.listAll();
            List<JobListingDto> dtos = new ArrayList<>(listings.size());
            for (JobListing job : listings) {
                dtos.add(new JobListingDto(job.name(), job.requiredEducation(), job.hourlyWage(),
                        job.location(), job.requiredClothing()));
            }
            return dtos;
        } catch (SQLException e) {
            throw new PersistenceFailureException(e);
        }
    }

    @PostMapping("/api/players/{username}/jobs/apply")
    public ApplyResponse apply(@PathVariable String username, @RequestBody ApplyRequest request) {
        GameServices services = factory.forPlayer(username);
        LocationGuard.requireAt(services, Location.EMPLOYMENT_OFFICE);

        ApplyOutcome outcome = services.jobs().apply(request.job());
        switch (outcome.status()) {
            case UNKNOWN_JOB:
                throw new UnknownJobException(request.job());
            case WEEK_OVER:
                throw new WeekOverException(username);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(username);
            case FAILED:
                throw new PersistenceFailureException("Job application failed for '" + username + "'");
            case INSUFFICIENT_EDUCATION:
                return new ApplyResponse(false, "INSUFFICIENT_EDUCATION", services.costs().applyJobMinutes(),
                        outcome.jobName(), null, assembler.assemble(username, services));
            default:
                return new ApplyResponse(true, null, services.costs().applyJobMinutes(),
                        outcome.jobName(), outcome.hourlyWage(), assembler.assemble(username, services));
        }
    }

    @PostMapping("/api/players/{username}/work")
    public WorkResponse work(@PathVariable String username) {
        GameServices services = factory.forPlayer(username);
        JobService jobService = services.jobs();

        String jobName = jobService.getJob();
        if (UNEMPLOYED.equals(jobName)) {
            throw new NoJobException(username);
        }
        String jobLocation = jobService.getLocation();
        Location current = services.travel().currentLocation();
        if (!current.displayName().equals(jobLocation)) {
            throw new WrongLocationException(jobLocation, current);
        }

        WorkOutcome outcome = services.stats().work();
        switch (outcome.status()) {
            case UNDERDRESSED:
                throw new UnderdressedException(username);
            case INSUFFICIENT_TIME:
                throw new InsufficientTimeException(username);
            case FAILED:
                throw new PersistenceFailureException("Work shift failed for '" + username + "'");
            default:
                return new WorkResponse(outcome.jobName(), outcome.hourlyWage(), services.costs().workMinutes(),
                        outcome.debtDocked(), assembler.assemble(username, services));
        }
    }
}
