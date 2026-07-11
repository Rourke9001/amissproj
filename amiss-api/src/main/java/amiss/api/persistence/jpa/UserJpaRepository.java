package amiss.api.persistence.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data projection/mutation methods over {@link UserEntity} ({@code tbluser}),
 * mirroring {@link JpaUserRepository}'s credentials-only port contract (KAN-34/KAN-54).
 * {@code name} is the entity id, so single-row lookups by name use the inherited {@link
 * #findById}, and inserting a brand-new row uses the inherited {@link #saveAndFlush}.
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, String> {

    @Query("select u.password from UserEntity u where u.name = :name")
    Optional<String> findPasswordHash(@Param("name") String name);

    @Modifying
    @Query("update UserEntity u set u.password = :password where u.name = :name")
    void updatePassword(@Param("name") String name, @Param("password") String password);
}
