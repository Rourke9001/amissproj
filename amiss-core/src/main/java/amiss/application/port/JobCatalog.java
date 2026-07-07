package amiss.application.port;

import amiss.domain.model.JobSpec;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only port over the {@code tbljob} catalog (KAN-53). */
public interface JobCatalog {

    Optional<JobSpec> byId(int jobId);

    List<JobSpec> byLocation(String location);

    List<JobSpec> all();

    /** Ids of the degrees required for {@code jobId} (0–2 per job). */
    Set<Integer> requiredDegrees(int jobId);
}
