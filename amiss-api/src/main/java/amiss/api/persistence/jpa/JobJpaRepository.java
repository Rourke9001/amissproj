package amiss.api.persistence.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data projection methods over {@link JobEntity} ({@code tbljobs}), mirroring
 * the core's {@code JdbcJobRepository}'s SQL 1:1 (KAN-34). Read-only reference data
 * — the game never inserts or updates rows here.
 */
public interface JobJpaRepository extends JpaRepository<JobEntity, String> {

    @Query("select j.education from JobEntity j where j.job = :job")
    Optional<Integer> findRequiredEducation(@Param("job") String job);

    @Query("select j.salary from JobEntity j where j.job = :job")
    Optional<Integer> findSalary(@Param("job") String job);

    @Query("select j.location from JobEntity j where j.job = :job")
    Optional<String> findLocation(@Param("job") String job);

    @Query("select j.clothing from JobEntity j where j.job = :job")
    Optional<Integer> findRequiredClothing(@Param("job") String job);

    /** Every job, ordered by location then education requirement, for {@link JpaJobRepository#listAll()}. */
    @Query("select j from JobEntity j order by j.location, j.education")
    List<JobEntity> findAllOrdered();
}
