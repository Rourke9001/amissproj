package amiss.api.persistence.jpa;

import amiss.application.port.DegreeCatalog;
import amiss.application.port.PersistenceFailureException;
import amiss.domain.model.DegreeSpec;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;

/**
 * JPA adapter for {@link DegreeCatalog} ({@code tbldegrees}), delegating to
 * {@link DegreeJpaRepository} (KAN-54). Read-only reference data — straight mapping from
 * {@link DegreeEntity} to {@link DegreeSpec}.
 *
 * <p>Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig}, matching the other JPA adapters' wiring style.
 */
public class JpaDegreeCatalog implements DegreeCatalog {

    private final DegreeJpaRepository degrees;

    public JpaDegreeCatalog(DegreeJpaRepository degrees) {
        this.degrees = degrees;
    }

    @Override
    public List<DegreeSpec> all() {
        return translate("list all degrees", () -> degrees.findAll().stream()
                .map(JpaDegreeCatalog::toDomain)
                .toList());
    }

    @Override
    public Optional<DegreeSpec> byId(int degreeId) {
        return translate("find degree", () -> degrees.findById(degreeId).map(JpaDegreeCatalog::toDomain));
    }

    private static DegreeSpec toDomain(DegreeEntity e) {
        return new DegreeSpec(e.getId(), e.getName(), e.getPrereqDegreeId());
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tbldegrees)", e);
        }
    }
}
