package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.api.config.CostsConfig;
import amiss.application.port.UserRepository;
import amiss.infrastructure.security.PasswordHasher;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration test for {@link JpaUserRepository} (the {@link UserRepository} port,
 * {@code tbluser}) against a real, freshly-migrated MySQL container (KAN-35/KAN-54).
 * Post-V6 {@code tbluser} holds credentials only — exercises the real INSERT/SELECT/UPDATE
 * against that shrunk schema, not just that the adapter *called* the repository. See
 * {@link MySqlITSupport} for the container/transaction wiring; every test cleans up the
 * rows it inserted in {@link #cleanUp()} — there is no free rollback.
 *
 * <p>{@link CostsConfig} is imported for the same reason as in {@link SavePortsAdapterIT}:
 * {@code PersistenceConfig}'s {@code SaveGameServices} bean needs an {@code ActionCosts}
 * bean to satisfy its dependencies at context startup, exactly as in production.
 */
@Import(CostsConfig.class)
class UserRepositoryIT extends MySqlITSupport {

    @Autowired
    private UserRepository users;

    @Autowired
    private UserJpaRepository userJpa;

    private final List<String> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String name : createdUsers) {
            userJpa.findById(name).ifPresent(userJpa::delete);
        }
    }

    private String newUser(String name, String passwordHash) {
        users.insertNewUser(name, passwordHash);
        createdUsers.add(name);
        return name;
    }

    // ---- insertNewUser: credentials only -------------------------------------

    @Test
    void insertNewUser_persistsExactlyTheGivenNameAndPassword() {
        String name = newUser("it-insert-credentials", "bcrypt-hash");

        UserEntity saved = userJpa.findById(name).orElseThrow();
        assertEquals(name, saved.getName());
        assertEquals("bcrypt-hash", saved.getPassword());
    }

    // ---- BCrypt round-trip --------------------------------------------------------

    @Test
    void insertNewUser_storesAndVerifiesABCryptHash() {
        String hash = PasswordHasher.hash("secret99");
        String name = newUser("it-bcrypt-roundtrip", hash);

        String stored = users.findPasswordHash(name).orElseThrow();

        assertEquals(60, stored.length());
        assertTrue(PasswordHasher.isHashed(stored));
        assertTrue(PasswordHasher.matches("secret99", stored));
        assertFalse(PasswordHasher.matches("wrong-password", stored));
    }

    @Test
    void updatePassword_replacesTheStoredHash() {
        String name = newUser("it-update-password", PasswordHasher.hash("first-pw"));

        String newHash = PasswordHasher.hash("second-pw");
        users.updatePassword(name, newHash);

        assertEquals(newHash, users.findPasswordHash(name).orElseThrow());
    }

    // ---- missing-row fallback, against the real DB --------------------------------------

    @Test
    void findPasswordHash_returnsEmpty_forAGhostUser() {
        assertTrue(users.findPasswordHash("it-ghost-user-does-not-exist").isEmpty());
    }
}
