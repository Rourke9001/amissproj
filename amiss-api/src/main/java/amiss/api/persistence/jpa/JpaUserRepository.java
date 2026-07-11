package amiss.api.persistence.jpa;

import amiss.application.port.PersistenceFailureException;
import amiss.application.port.UserRepository;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link UserRepository} ({@code tbluser}), delegating to
 * {@link UserJpaRepository} (KAN-34/KAN-54). Each mutating method is its own transaction,
 * mirroring the port's original autocommit-per-call behaviour.
 *
 * <p>Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig} so exactly one {@link UserRepository} bean
 * exists.
 */
public class JpaUserRepository implements UserRepository {

    private final UserJpaRepository users;

    public JpaUserRepository(UserJpaRepository users) {
        this.users = users;
    }

    @Override
    public Optional<String> findPasswordHash(String name) {
        return translate("find password hash", () -> users.findPasswordHash(name));
    }

    @Override
    @Transactional
    public void updatePassword(String name, String passwordHash) {
        translateRun("update password", () -> users.updatePassword(name, passwordHash));
    }

    /** Inserts a brand-new player row holding just the given credentials (KAN-54). */
    @Override
    @Transactional
    public void insertNewUser(String name, String passwordHash) {
        translateRun("insert new user", () -> {
            UserEntity user = new UserEntity();
            user.setName(name);
            user.setPassword(passwordHash);
            // saveAndFlush: the id is assigned (no IDENTITY), so a plain save() would
            // defer the INSERT to the proxy's commit — outside this translation block —
            // letting constraint violations escape untranslated.
            users.saveAndFlush(user);
        });
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tbluser)", e);
        }
    }

    private void translateRun(String operation, Runnable call) {
        translate(operation, () -> {
            call.run();
            return null;
        });
    }
}
