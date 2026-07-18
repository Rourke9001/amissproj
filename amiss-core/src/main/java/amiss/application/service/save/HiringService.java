package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.JobCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.application.port.Turndowns;
import amiss.domain.model.JobSpec;
import amiss.domain.model.SaveState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * The Employment Office hiring rule (KAN-53), wiki-exact. Applying charges the
 * interview time (clamped to the clock) win or lose; requirements are checked in the
 * wiki's order (education → experience → dependability → clothing → luck), every failing named
 * check is reported, and a failed luck roll turns the job down for the rest of the
 * round. Weeks 1–4 report a dependability shortfall as {@code NO_OPENINGS} (wiki
 * suppression rule). The Cook at Monolith Burgers skips every check.
 *
 * <p>Happiness: +3 on hire, −1 on any rejection. Hire side-effects: wage is the listed
 * wage, +2 experience (cap-exempt job-switch bonus), dependability floored up to 10.
 */
public class HiringService {

    /** Weeks during which "Poor Work History" is suppressed to "No Openings". */
    private static final int POOR_HISTORY_SUPPRESSED_UNTIL_ROUND = 4;
    private static final int HIRE_HAPPINESS = 3;
    private static final int REJECT_HAPPINESS = -1;
    private static final int JOB_SWITCH_EXPERIENCE_BONUS = 2;
    private static final int HIRE_DEPENDABILITY_FLOOR = 10;

    private final SaveRepository saves;
    private final JobCatalog jobs;
    private final SaveDegrees degrees;
    private final Turndowns turndowns;
    private final ActionCosts costs;
    private final IntSupplier roll1to100;
    private final EconomyService economy;

    public HiringService(SaveRepository saves, JobCatalog jobs, SaveDegrees degrees,
            Turndowns turndowns, ActionCosts costs, IntSupplier roll1to100, EconomyService economy) {
        this.saves = saves;
        this.jobs = jobs;
        this.degrees = degrees;
        this.turndowns = turndowns;
        this.costs = costs;
        this.roll1to100 = roll1to100;
        this.economy = economy;
    }

    public HireOutcome apply(SaveState save, int jobId) {
        Optional<JobSpec> listing = jobs.byId(jobId);
        if (listing.isEmpty()) {
            return new HireOutcome(HireOutcome.Status.UNKNOWN_JOB, List.of(), 0,
                    save.timeMinutes(), null, -1);
        }
        JobSpec job = listing.get();
        if (save.weekOver()) {
            return new HireOutcome(HireOutcome.Status.WEEK_OVER, List.of(), 0, 0, job.name(), -1);
        }

        int charged = save.spendUpTo(costs.applyJobMinutes());

        if (job.alwaysHired()) {
            return hire(save, job, charged);
        }

        Set<Integer> earned = degrees.earned(save.id());
        List<HireOutcome.Reason> reasons = new ArrayList<>();
        if (!earned.containsAll(jobs.requiredDegrees(jobId))) {
            reasons.add(HireOutcome.Reason.NOT_ENOUGH_EDUCATION);
        }
        if (save.experience() < job.reqExperience()) {
            reasons.add(HireOutcome.Reason.NOT_ENOUGH_EXPERIENCE);
        }
        if (save.dependability() < job.effectiveReqDependability()) {
            reasons.add(save.round() <= POOR_HISTORY_SUPPRESSED_UNTIL_ROUND
                    ? HireOutcome.Reason.NO_OPENINGS
                    : HireOutcome.Reason.POOR_WORK_HISTORY);
        }
        if (!save.hasClothingLevel(job.reqClothing())) {
            reasons.add(HireOutcome.Reason.NOT_ENOUGH_CLOTHING);
        }
        if (!reasons.isEmpty()) {
            return reject(save, job, reasons, charged);
        }

        if (turndowns.isTurnedDown(save.id(), jobId, save.round())) {
            return reject(save, job, List.of(HireOutcome.Reason.NO_OPENINGS), charged);
        }
        int luck = StatFormulas.luck(save.dependability(), save.experience(), earned.size());
        if (roll1to100.getAsInt() > luck) {
            turndowns.record(save.id(), jobId, save.round());
            return reject(save, job, List.of(HireOutcome.Reason.NO_OPENINGS), charged);
        }

        return hire(save, job, charged);
    }

    private HireOutcome hire(SaveState save, JobSpec job, int charged) {
        int listedWage = economy.price(job.wage(), save);
        save.setJobId(job.id());
        save.setWage(listedWage);
        save.setExperience(save.experience() + JOB_SWITCH_EXPERIENCE_BONUS);
        save.setDependability(Math.max(save.dependability(), HIRE_DEPENDABILITY_FLOOR));
        save.addHappiness(HIRE_HAPPINESS);
        saves.update(save);
        return HireOutcome.hired(charged, save.timeMinutes(), job.name(), listedWage);
    }

    private HireOutcome reject(SaveState save, JobSpec job, List<HireOutcome.Reason> reasons, int charged) {
        save.addHappiness(REJECT_HAPPINESS);
        saves.update(save);
        return HireOutcome.rejected(reasons, charged, save.timeMinutes(), job.name());
    }
}
