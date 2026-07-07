package amiss.api.persistence.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code tblsave_turndowns} — per-round "No Openings" blocks. */
public interface SaveTurndownJpaRepository extends JpaRepository<SaveTurndownEntity, SaveTurndownId> {

    Optional<SaveTurndownEntity> findBySaveIdAndJobId(Long saveId, Integer jobId);
}
