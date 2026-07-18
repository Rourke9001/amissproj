package amiss.application.service.save;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;

/**
 * The Relax action at Home (KAN-23), wiki-exact: 6h, +3 Relaxation (max 50), and +2
 * Happiness on the first Relax of a turn only — repeat Relaxes the same turn still
 * raise the stat but grant no further happiness.
 */
public class RelaxService {

    static final int RELAXATION_GAIN = 3;
    static final int FIRST_RELAX_HAPPINESS = 2;

    public record RelaxOutcome(Status status, int remainingMinutes, int relaxation) {
        public enum Status {
            OK, WEEK_OVER, INSUFFICIENT_TIME
        }
    }

    private final SaveRepository saves;
    private final ActionCosts costs;

    public RelaxService(SaveRepository saves, ActionCosts costs) {
        this.saves = saves;
        this.costs = costs;
    }

    public RelaxOutcome relax(SaveState save) {
        if (save.weekOver()) {
            return new RelaxOutcome(RelaxOutcome.Status.WEEK_OVER, save.timeMinutes(), save.relaxation());
        }
        int time = save.timeMinutes();
        int remaining = time - costs.relaxMinutes();
        if (remaining < 0) {
            return new RelaxOutcome(RelaxOutcome.Status.INSUFFICIENT_TIME, time, save.relaxation());
        }
        save.setTimeMinutes(remaining);
        save.addRelaxation(RELAXATION_GAIN);
        if (!save.relaxedThisTurn()) {
            save.addHappiness(FIRST_RELAX_HAPPINESS);
            save.setRelaxedThisTurn(true);
        }
        saves.update(save);
        return new RelaxOutcome(RelaxOutcome.Status.OK, remaining, save.relaxation());
    }
}
