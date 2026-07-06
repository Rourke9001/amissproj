package amiss.api.persistence.jpa;

import amiss.application.port.JobRepository;
import amiss.application.port.PersistenceFailureException;
import amiss.domain.model.JobListing;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;

/**
 * JPA adapter for {@link JobRepository} ({@code tbljobs}), delegating to
 * {@link JobJpaRepository} (KAN-34). Read-only: reproduces the core's {@code
 * JdbcJobRepository}'s exact missing-job fallbacks.
 *
 * <p>Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig}, matching the JDBC adapter's wiring style.
 */
public class JpaJobRepository implements JobRepository {

    private final JobJpaRepository jobs;

    public JpaJobRepository(JobJpaRepository jobs) {
        this.jobs = jobs;
    }

    @Override
    public int getRequiredEducation(String job) {
        return translate("get required education", () -> jobs.findRequiredEducation(job).orElse(-1));
    }

    @Override
    public int getSalary(String job) {
        return translate("get salary", () -> jobs.findSalary(job).orElse(-1));
    }

    @Override
    public String getLocation(String job) {
        return translate("get location", () -> jobs.findLocation(job).orElse(null));
    }

    @Override
    public String getRequiredClothing(String job) {
        return translate("get required clothing",
                () -> jobs.findRequiredClothing(job).map(String::valueOf).orElse(null));
    }

    @Override
    public List<JobListing> listAll() {
        return translate("list all jobs", () -> jobs.findAllOrdered().stream()
                .map(e -> new JobListing(e.getJob(), e.getEducation(), e.getSalary(), e.getLocation(),
                        e.getClothing()))
                .toList());
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tbljobs)", e);
        }
    }
}
