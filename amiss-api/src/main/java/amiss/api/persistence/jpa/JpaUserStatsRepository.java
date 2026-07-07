package amiss.api.persistence.jpa;

import amiss.application.port.PersistenceFailureException;
import amiss.application.port.UserStatsRepository;
import jakarta.persistence.PersistenceException;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link UserStatsRepository} ({@code tbluserstats}), delegating to
 * {@link UserStatsJpaRepository} (KAN-34). Reproduces the core's {@code
 * JdbcUserStatsRepository}'s exact missing-row fallbacks; each mutating method is
 * its own transaction, mirroring the JDBC adapter's autocommit-per-call behaviour.
 * Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig}, matching the JDBC adapter's wiring style.
 */
public class JpaUserStatsRepository implements UserStatsRepository {

    private final UserStatsJpaRepository stats;
    private final UserJpaRepository users;

    public JpaUserStatsRepository(UserStatsJpaRepository stats, UserJpaRepository users) {
        this.stats = stats;
        this.users = users;
    }

    // ---- reads -------------------------------------------------------------

    @Override
    public int getEducation(String name) {
        return translate("get education", () -> stats.findEducation(name).orElse(-1));
    }

    @Override
    public int getEduprog(String name) {
        return translate("get eduprog", () -> stats.findEduprog(name).orElse(-1));
    }

    @Override
    public String getWork(String name) {
        return translate("get work", () -> stats.findWork(name).map(String::valueOf).orElse(null));
    }

    @Override
    public String getHappiness(String name) {
        return translate("get happiness", () -> stats.findHappiness(name).map(String::valueOf).orElse(null));
    }

    // ---- writes --------------------------------------------------------------

    @Override
    @Transactional
    public void updateEducation(String name, int education) {
        translateRun("update education", () -> stats.updateEducation(name, education));
    }

    @Override
    @Transactional
    public void updateEduprog(String name, int eduprog) {
        translateRun("update eduprog", () -> stats.updateEduprog(name, eduprog));
    }

    @Override
    @Transactional
    public void incrementWork(String name) {
        translateRun("increment work", () -> stats.incrementWork(name));
    }

    @Override
    @Transactional
    public void incrementHappiness(String name) {
        translateRun("increment happiness", () -> stats.incrementHappiness(name));
    }

    /**
     * Inserts the starting (all-zero) stats row for a brand-new player, mirroring {@code
     * JdbcUserStatsRepository#insertNewStats}'s positional INSERT. The shared-primary-key
     * association is set via {@link UserJpaRepository#getReferenceById}, an unloaded proxy
     * reference — no extra SELECT, exactly one INSERT reaches the database.
     */
    @Override
    @Transactional
    public void insertNewStats(String name) {
        translateRun("insert new stats", () -> {
            UserStatsEntity entity = new UserStatsEntity();
            entity.setUser(users.getReferenceById(name));
            entity.setHappiness(0);
            entity.setEducation(0);
            entity.setWork(0);
            entity.setEduprog(0);
            // saveAndFlush: the @MapsId id is assigned (no IDENTITY), so a plain save()
            // would defer the INSERT to the proxy's commit — outside this translation
            // block — letting the tbluser FK violation escape untranslated.
            stats.saveAndFlush(entity);
        });
    }

    @Override
    @Transactional
    public void resetStats(String name) {
        translateRun("reset stats", () -> stats.resetStats(name));
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tbluserstats)", e);
        }
    }

    private void translateRun(String operation, Runnable call) {
        translate(operation, () -> {
            call.run();
            return null;
        });
    }
}
