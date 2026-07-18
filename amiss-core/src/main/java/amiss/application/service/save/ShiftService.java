package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.JobCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.domain.model.JobSpec;
import amiss.domain.model.SaveState;

/**
 * The work-session rule (KAN-53), wiki-exact. A full session is 6h paying wage × 8;
 * a shorter clock pro-rates the pay ({@code wage*8*minutes/360}, integer) and still
 * succeeds — over-clock actions clamp, they don't reject. Attempting to work with
 * dependability more than 5 below the job's (effective) requirement fires the player
 * on the spot (−3 happiness, no time charged); 3–5 below only warns. A paying session
 * grants +1 experience and +1 dependability, each only below its cap
 * ({@link StatFormulas}). While in rent debt, half the pay is garnished toward the
 * debt plus a R2 interest fee.
 */
public class ShiftService {

    private static final int FULL_SESSION_PAY_MULTIPLIER = 8;
    private static final int FIRED_BELOW_REQ = 5;
    private static final int WARNING_BELOW_REQ = 3;
    private static final int FIRED_HAPPINESS = -3;
    private static final int GARNISH_INTEREST_FEE = 2;

    private final SaveRepository saves;
    private final JobCatalog jobs;
    private final SaveDegrees degrees;
    private final ActionCosts costs;

    public ShiftService(SaveRepository saves, JobCatalog jobs, SaveDegrees degrees, ActionCosts costs) {
        this.saves = saves;
        this.jobs = jobs;
        this.degrees = degrees;
        this.costs = costs;
    }

    public ShiftOutcome work(SaveState save) {
        if (!save.employed()) {
            return new ShiftOutcome(ShiftOutcome.Status.NO_JOB, false, -1, -1, 0, 0,
                    save.timeMinutes(), null);
        }
        JobSpec job = jobs.byId(save.jobId()).orElseThrow(
                () -> new IllegalStateException("Held job " + save.jobId() + " missing from catalog"));

        if (!save.hasClothingLevel(job.reqClothing())) {
            return new ShiftOutcome(ShiftOutcome.Status.UNDERDRESSED, false, -1, -1, 0, 0,
                    save.timeMinutes(), job.name());
        }

        int reqDep = job.effectiveReqDependability();
        if (save.dependability() < reqDep - FIRED_BELOW_REQ) {
            save.setJobId(null);
            save.setWage(null);
            save.addHappiness(FIRED_HAPPINESS);
            saves.update(save);
            return new ShiftOutcome(ShiftOutcome.Status.FIRED, false, -1, -1, 0, 0,
                    save.timeMinutes(), job.name());
        }
        boolean warning = save.dependability() <= reqDep - WARNING_BELOW_REQ;

        if (save.weekOver()) {
            return new ShiftOutcome(ShiftOutcome.Status.WEEK_OVER, warning, -1, -1, 0, 0, 0, job.name());
        }

        int minutes = save.spendUpTo(costs.workMinutes());
        int wage = java.util.Objects.requireNonNull(save.wage(),
                "employed save " + save.id() + " has no wage snapshot (V8 backfill)");
        int pay = wage * FULL_SESSION_PAY_MULTIPLIER * minutes / costs.workMinutes();

        int garnished = 0;
        int net = pay;
        if (save.debt() > 0 && pay > 0) {
            garnished = Math.min(pay / 2, save.debt());
            save.setDebt(save.debt() - garnished);
            net = pay - garnished - GARNISH_INTEREST_FEE;
        }
        save.setCash(save.cash() + net);

        if (pay > 0) {
            int degreeCount = degrees.earned(save.id()).size();
            if (save.experience() < StatFormulas.maxExperience(job, degreeCount)) {
                save.setExperience(save.experience() + 1);
            }
            if (save.dependability() < StatFormulas.maxDependability(job, degreeCount)) {
                save.setDependability(save.dependability() + 1);
            }
        }
        saves.update(save);
        return new ShiftOutcome(ShiftOutcome.Status.OK, warning, pay, net, garnished,
                minutes, save.timeMinutes(), job.name());
    }
}
