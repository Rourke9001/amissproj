package amiss.api.web;

import amiss.api.error.NoJobException;
import amiss.api.error.UnderdressedException;
import amiss.api.error.UnknownJobException;
import amiss.api.error.WeekOverException;
import amiss.api.error.WrongLocationException;
import amiss.api.web.dto.ApplyRequest;
import amiss.api.web.dto.ApplyResponse;
import amiss.api.web.dto.JobListingDto;
import amiss.api.web.dto.WorkResponse;
import amiss.application.port.JobCatalog;
import amiss.application.service.save.EconomyService;
import amiss.application.service.save.HireOutcome;
import amiss.application.service.save.SaveGameServices;
import amiss.application.service.save.ShiftOutcome;
import amiss.domain.board.Location;
import amiss.domain.model.JobSpec;
import amiss.domain.model.SaveState;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The job catalog, applying and working (KAN-54: cut over to {@code /api/saves/{saveId}}
 * plus the V5 {@link JobCatalog}; KAN-48: listings now save-scoped and economy-priced).
 * The listing is deliberately requirement-free — {@code apply} is the only channel a
 * player learns why an application was turned down; displayed wages fluctuate with the
 * save's economy and equal the wage that would be snapshotted if hired. Applying
 * is only reachable at the {@link Location#EMPLOYMENT_OFFICE}; working requires standing at
 * the held job's own location (read from the catalog), checked inline like the legacy
 * controller since it varies per job rather than being a fixed {@link Location}.
 */
@RestController
public class EmploymentController {

    private final SaveScope scope;
    private final SaveGameServices services;
    private final PlayerStateAssembler assembler;
    private final JobCatalog jobCatalog;

    public EmploymentController(SaveScope scope, SaveGameServices services, PlayerStateAssembler assembler,
            JobCatalog jobCatalog) {
        this.scope = scope;
        this.services = services;
        this.assembler = assembler;
        this.jobCatalog = jobCatalog;
    }

    @GetMapping("/api/saves/{saveId}/jobs")
    public List<JobListingDto> jobs(@PathVariable long saveId, Authentication authentication,
            @RequestParam(required = false) String location) {
        SaveState save = scope.require(saveId, authentication);
        EconomyService economy = services.economy();
        List<JobSpec> listings = location == null ? jobCatalog.all() : jobCatalog.byLocation(location);
        return listings.stream()
                .map(job -> new JobListingDto(job.id(), job.name(), job.location(),
                        economy.price(job.wage(), save)))
                .toList();
    }

    @PostMapping("/api/saves/{saveId}/jobs/apply")
    public ApplyResponse apply(@PathVariable long saveId, Authentication authentication,
            @RequestBody ApplyRequest request) {
        SaveState save = scope.require(saveId, authentication);
        LocationGuard.requireAt(services.travel(), save, Location.EMPLOYMENT_OFFICE);

        int jobId = request.jobId() == null ? -1 : request.jobId();
        HireOutcome outcome = services.hiring().apply(save, jobId);
        switch (outcome.status()) {
            case UNKNOWN_JOB:
                throw new UnknownJobException(jobId);
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            default:
                boolean hired = outcome.status() == HireOutcome.Status.HIRED;
                List<String> reasons = outcome.reasons().stream().map(Enum::name).toList();
                return new ApplyResponse(hired, reasons, outcome.minutesCharged(), outcome.jobName(),
                        hired ? outcome.wage() : null, assembler.assemble(services, save));
        }
    }

    @PostMapping("/api/saves/{saveId}/work")
    public WorkResponse work(@PathVariable long saveId, Authentication authentication) {
        SaveState save = scope.require(saveId, authentication);

        if (!save.employed()) {
            throw new NoJobException(saveId);
        }
        JobSpec job = jobCatalog.byId(save.jobId())
                .orElseThrow(() -> new IllegalStateException("Held job " + save.jobId() + " missing from catalog"));
        Location current = services.travel().currentLocation(save);
        if (!current.displayName().equals(job.location())) {
            throw new WrongLocationException(job.location(), current);
        }

        ShiftOutcome outcome = services.shifts().work(save);
        switch (outcome.status()) {
            case NO_JOB:
                throw new NoJobException(saveId);
            case UNDERDRESSED:
                throw new UnderdressedException(saveId);
            case WEEK_OVER:
                throw new WeekOverException(saveId);
            default:
                return new WorkResponse(outcome.status().name(), outcome.warning(), outcome.jobName(),
                        outcome.pay(), outcome.netPaid(), outcome.garnished(), outcome.minutesCharged(),
                        assembler.assemble(services, save));
        }
    }
}
