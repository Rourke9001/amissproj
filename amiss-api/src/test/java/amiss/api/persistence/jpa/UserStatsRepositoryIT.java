package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.application.port.PersistenceFailureException;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Integration test for {@link JpaUserStatsRepository} (the {@link UserStatsRepository}
 * port, {@code tbluserstats}) against a real, freshly-migrated MySQL container (KAN-35).
 *
 * <p>{@code tbluserstats.name} is both the primary key and an {@code ON DELETE CASCADE}
 * foreign key to {@code tbluser.name} ({@code V1__baseline_schema.sql}), so every stats
 * row here needs a parent {@code tbluser} row first — created via the real
 * {@link UserRepository} port, not a shortcut insert.
 *
 * <p>See {@link MySqlITSupport} for the container/transaction wiring. Every test cleans
 * up the user (and cascaded stats) row(s) it created in {@link #cleanUp()}.
 */
class UserStatsRepositoryIT extends MySqlITSupport {

    @Autowired
    private UserRepository users;

    @Autowired
    private UserStatsRepository stats;

    @Autowired
    private UserJpaRepository userJpa;

    @Autowired
    private UserStatsJpaRepository statsJpa;

    private final List<String> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String name : createdUsers) {
            // ON DELETE CASCADE takes the stats row with it; guard against the
            // cascade-delete test, which already removed its own user row.
            userJpa.findById(name).ifPresent(userJpa::delete);
        }
    }

    private String newUserWithStats(String name) {
        users.insertNewUser(name, "bcrypt-hash");
        createdUsers.add(name);
        stats.insertNewStats(name);
        return name;
    }

    @Test
    void insertNewStats_readsBackAnAllZeroRow() {
        String name = newUserWithStats("it-stats-insert-defaults");

        assertEquals(0, stats.getEducation(name));
        assertEquals(0, stats.getEduprog(name));
        assertEquals("0", stats.getWork(name));
        assertEquals("0", stats.getHappiness(name));
    }

    @Test
    void updateEducationAndUpdateEduprog_readBackTheExactValuesSet() {
        String name = newUserWithStats("it-stats-update-education-eduprog");

        stats.updateEducation(name, 5);
        stats.updateEduprog(name, 3);

        assertEquals(5, stats.getEducation(name));
        assertEquals(3, stats.getEduprog(name));
    }

    @Test
    void incrementWorkAndIncrementHappiness_addOneEachCall() {
        String name = newUserWithStats("it-stats-increment-twice");

        stats.incrementWork(name);
        assertEquals("1", stats.getWork(name));
        stats.incrementWork(name);
        assertEquals("2", stats.getWork(name));

        stats.incrementHappiness(name);
        assertEquals("1", stats.getHappiness(name));
        stats.incrementHappiness(name);
        assertEquals("2", stats.getHappiness(name));
    }

    /**
     * Mirrors {@code JdbcUserStatsRepository#resetStats}'s SQL exactly: {@code happiness},
     * {@code education} and {@code work} go to 0, but {@code eduprog} — the player's
     * in-progress study counter — is deliberately left alone.
     */
    @Test
    void resetStats_zeroesHappinessEducationWork_butLeavesEduprogUntouched() {
        String name = newUserWithStats("it-stats-reset");
        stats.updateEducation(name, 6);
        stats.updateEduprog(name, 7);
        stats.incrementWork(name);
        stats.incrementHappiness(name);

        stats.resetStats(name);

        assertEquals(0, stats.getEducation(name));
        assertEquals("0", stats.getWork(name));
        assertEquals("0", stats.getHappiness(name));
        assertEquals(7, stats.getEduprog(name), "resetStats must not touch eduprog");
    }

    /**
     * Pins the port contract on a real constraint violation: {@code insertNewStats} for a
     * user with no {@code tbluser} parent row hits the FK and must surface as
     * {@link PersistenceFailureException}. This originally escaped as a raw
     * {@link DataIntegrityViolationException} because the {@code @MapsId} id is assigned
     * (not IDENTITY-generated), so {@code save()} deferred the INSERT to the proxy's
     * commit — outside the adapter's translation block. The adapter now uses
     * {@code saveAndFlush()} precisely so this test can hold.
     */
    @Test
    void insertNewStats_forAUserWithNoParentRow_translatesTheForeignKeyViolation() {
        String ghost = "it-stats-ghost-no-parent-user";

        assertThrows(PersistenceFailureException.class, () -> stats.insertNewStats(ghost));
    }

    @Test
    void deletingTheUserRow_cascadesTheStatsRow() {
        String name = newUserWithStats("it-stats-cascade-delete");
        assertTrue(statsJpa.existsById(name));

        userJpa.deleteById(name);

        assertFalse(statsJpa.existsById(name), "ON DELETE CASCADE should have removed the stats row too");
    }
}
