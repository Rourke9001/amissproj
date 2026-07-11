package amiss.api.persistence.jpa;

import amiss.application.port.PersistenceFailureException;
import amiss.application.port.SaveDegrees;
import jakarta.persistence.PersistenceException;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link SaveDegrees} ({@code tblsave_degrees}), delegating to
 * {@link SaveDegreeJpaRepository} (KAN-54). {@code award} checks existence first so
 * awarding the same degree twice (e.g. a re-run rules path) is a no-op rather than a
 * unique-constraint violation — degrees can never be lost, but they are never duplicated
 * either.
 *
 * <p>Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig}, matching the other JPA adapters' wiring style.
 */
public class JpaSaveDegrees implements SaveDegrees {

    private final SaveDegreeJpaRepository saveDegrees;

    public JpaSaveDegrees(SaveDegreeJpaRepository saveDegrees) {
        this.saveDegrees = saveDegrees;
    }

    @Override
    public Set<Integer> earned(long saveId) {
        return translate("find earned degrees", () -> saveDegrees.findBySaveId(saveId).stream()
                .map(SaveDegreeEntity::getDegreeId)
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Override
    @Transactional
    public void award(long saveId, int degreeId) {
        translateRun("award degree", () -> {
            SaveDegreeId id = new SaveDegreeId(saveId, degreeId);
            if (saveDegrees.existsById(id)) {
                return;
            }
            // saveAndFlush: no IDENTITY id here either, so a plain save() would defer
            // the INSERT past this translation block — see JpaUserRepository's note.
            saveDegrees.saveAndFlush(new SaveDegreeEntity(saveId, degreeId));
        });
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tblsave_degrees)", e);
        }
    }

    private void translateRun(String operation, Runnable call) {
        translate(operation, () -> {
            call.run();
            return null;
        });
    }
}
