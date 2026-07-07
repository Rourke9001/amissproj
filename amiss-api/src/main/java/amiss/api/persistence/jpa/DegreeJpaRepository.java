package amiss.api.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the {@code tbldegrees} catalog (read-only reference data). */
public interface DegreeJpaRepository extends JpaRepository<DegreeEntity, Integer> {
}
