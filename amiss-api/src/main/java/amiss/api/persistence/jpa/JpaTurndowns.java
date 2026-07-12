package amiss.api.persistence.jpa;

import amiss.application.port.PersistenceFailureException;
import amiss.application.port.Turndowns;
import jakarta.persistence.PersistenceException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link Turndowns} ({@code tblsave_turndowns}), delegating to
 * {@link SaveTurndownJpaRepository} (KAN-54). {@code record} is an upsert: an existing
 * (save, job) row has its round refreshed in place rather than being duplicated, since
 * the composite key already forbids more than one row per pair.
 *
 * <p>Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig}, matching the other JPA adapters' wiring style.
 */
public class JpaTurndowns implements Turndowns {

    private final SaveTurndownJpaRepository turndowns;

    public JpaTurndowns(SaveTurndownJpaRepository turndowns) {
        this.turndowns = turndowns;
    }

    @Override
    public boolean isTurnedDown(long saveId, int jobId, int round) {
        return translate("check turndown", () -> turndowns.findBySaveIdAndJobId(saveId, jobId)
                .map(e -> e.getRound() == round)
                .orElse(false));
    }

    @Override
    @Transactional
    public void record(long saveId, int jobId, int round) {
        translateRun("record turndown", () -> {
            turndowns.findBySaveIdAndJobId(saveId, jobId).ifPresentOrElse(
                    existing -> {
                        existing.setRound(round);
                        turndowns.saveAndFlush(existing);
                    },
                    () -> turndowns.saveAndFlush(new SaveTurndownEntity(saveId, jobId, round)));
        });
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tblsave_turndowns)", e);
        }
    }

    private void translateRun(String operation, Runnable call) {
        translate(operation, () -> {
            call.run();
            return null;
        });
    }
}
