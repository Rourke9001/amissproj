package amiss.api.persistence.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code tblsave} — an account's saved games. */
public interface SaveJpaRepository extends JpaRepository<SaveEntity, Long> {

    List<SaveEntity> findByOwnerOrderByUpdatedAtDesc(String owner);

    /** Ownership lookup for the save-scope guard without loading the whole row. */
    Optional<SaveEntity> findByIdAndOwner(Long id, String owner);
}
