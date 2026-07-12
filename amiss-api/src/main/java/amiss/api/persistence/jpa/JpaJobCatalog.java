package amiss.api.persistence.jpa;

import amiss.application.port.JobCatalog;
import amiss.application.port.PersistenceFailureException;
import amiss.domain.model.JobSpec;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;

/**
 * JPA adapter for {@link JobCatalog} ({@code tbljob}), delegating to
 * {@link JobCatalogJpaRepository} (KAN-54). Read-only reference data — straight mapping
 * from {@link JobCatalogEntity} to {@link JobSpec}.
 *
 * <p>Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig}, matching the other JPA adapters' wiring style.
 */
public class JpaJobCatalog implements JobCatalog {

    private final JobCatalogJpaRepository jobs;

    public JpaJobCatalog(JobCatalogJpaRepository jobs) {
        this.jobs = jobs;
    }

    @Override
    public Optional<JobSpec> byId(int jobId) {
        return translate("find job", () -> jobs.findById(jobId).map(JpaJobCatalog::toDomain));
    }

    @Override
    public List<JobSpec> byLocation(String location) {
        return translate("list jobs by location", () -> jobs.findByLocationOrderById(location).stream()
                .map(JpaJobCatalog::toDomain)
                .toList());
    }

    @Override
    public List<JobSpec> all() {
        return translate("list all jobs", () -> jobs.findAll().stream()
                .map(JpaJobCatalog::toDomain)
                .toList());
    }

    @Override
    public Set<Integer> requiredDegrees(int jobId) {
        return translate("find required degrees", () -> jobs.findById(jobId)
                .map(JobCatalogEntity::getRequiredDegreeIds)
                .orElseGet(Set::of));
    }

    private static JobSpec toDomain(JobCatalogEntity e) {
        return new JobSpec(e.getId(), e.getJob(), e.getLocation(), e.getWage(),
                e.getReqExperience(), e.getReqDependability(), e.getReqClothing());
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tbljob)", e);
        }
    }
}
