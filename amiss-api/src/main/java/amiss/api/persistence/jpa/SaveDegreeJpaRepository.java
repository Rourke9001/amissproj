package amiss.api.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code tblsave_degrees} — degrees a save has earned. */
public interface SaveDegreeJpaRepository extends JpaRepository<SaveDegreeEntity, SaveDegreeId> {

    List<SaveDegreeEntity> findBySaveId(Long saveId);
}
