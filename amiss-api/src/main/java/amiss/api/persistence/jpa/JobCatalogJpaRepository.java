package amiss.api.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the {@code tbljob} catalog (read-only reference data). */
public interface JobCatalogJpaRepository extends JpaRepository<JobCatalogEntity, Integer> {

    List<JobCatalogEntity> findByLocationOrderById(String location);
}
