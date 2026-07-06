package amiss.api.persistence.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data projection/mutation methods over {@link UserStatsEntity} ({@code
 * tbluserstats}), mirroring the core's {@code JdbcUserStatsRepository}'s SQL 1:1
 * (KAN-34).
 */
public interface UserStatsJpaRepository extends JpaRepository<UserStatsEntity, String> {

    @Query("select s.education from UserStatsEntity s where s.name = :name")
    Optional<Integer> findEducation(@Param("name") String name);

    @Query("select s.eduprog from UserStatsEntity s where s.name = :name")
    Optional<Integer> findEduprog(@Param("name") String name);

    @Query("select s.work from UserStatsEntity s where s.name = :name")
    Optional<Integer> findWork(@Param("name") String name);

    @Query("select s.happiness from UserStatsEntity s where s.name = :name")
    Optional<Integer> findHappiness(@Param("name") String name);

    @Modifying
    @Query("update UserStatsEntity s set s.education = :education where s.name = :name")
    void updateEducation(@Param("name") String name, @Param("education") int education);

    @Modifying
    @Query("update UserStatsEntity s set s.eduprog = :eduprog where s.name = :name")
    void updateEduprog(@Param("name") String name, @Param("eduprog") int eduprog);

    @Modifying
    @Query("update UserStatsEntity s set s.work = s.work + 1 where s.name = :name")
    void incrementWork(@Param("name") String name);

    @Modifying
    @Query("update UserStatsEntity s set s.happiness = s.happiness + 1 where s.name = :name")
    void incrementHappiness(@Param("name") String name);

    /** Resets happiness/education/work to 0 (leaves eduprog), mirroring the JDBC reset. */
    @Modifying
    @Query("update UserStatsEntity s set s.happiness = 0, s.education = 0, s.work = 0 where s.name = :name")
    void resetStats(@Param("name") String name);
}
