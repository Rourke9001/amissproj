package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.application.port.UserRepository;
import amiss.infrastructure.security.PasswordHasher;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for {@link JpaUserRepository} (the {@link UserRepository} port,
 * {@code tbluser}) against a real, freshly-migrated MySQL container (KAN-35). Exercises
 * the exact behaviour {@code JpaUserRepositoryTest}'s mocks only assert was *called*:
 * real defaults, real atomic conditional bank UPDATEs, and real missing-row SQL.
 *
 * <p>See {@link MySqlITSupport} for the container/transaction wiring. Every test cleans
 * up the rows it inserted in {@link #cleanUp()} — there is no free rollback.
 */
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

    // ---- insertNewUser defaults --------------------------------------------------

    @Test
    void insertNewUser_readsBackEveryGameDefault() {
        String name = newUser("it-insert-defaults", "bcrypt-hash");

        assertEquals(0, users.getXpos(name));
        assertEquals(2, users.getYpos(name));
        assertEquals(4320, users.getTime(name));
        assertEquals(100, users.getCash(name));
        assertEquals(1, users.getRound(name));
        assertEquals("Unemployed", users.getJob(name));
        assertEquals("1", users.getUserClothing(name));
        assertEquals(1, users.getRent(name));
        assertEquals(0, users.getEat(name));
        assertEquals(0, users.getDebt(name));
        // Not the -1 missing-row fallback: a freshly-inserted row's bank really is 0.
        assertEquals(0, users.getBank(name));
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

    // ---- absolute setters -----------------------------------------------------------

    @Test
    void updatePositionTimeCash_readBackTheExactValuesSet() {
        String name = newUser("it-update-absolutes", "bcrypt-hash");

        users.updatePosition(name, 5, 7);
        users.updateTime(name, 1000);
        users.updateCash(name, 250);

        assertEquals(5, users.getXpos(name));
        assertEquals(7, users.getYpos(name));
        assertEquals(1000, users.getTime(name));
        assertEquals(250, users.getCash(name));
    }

    // ---- debt arithmetic --------------------------------------------------------------

    @Test
    void addDebtAndSubtractDebt_accumulateAgainstTheCurrentValue() {
        String name = newUser("it-debt-arithmetic", "bcrypt-hash");

        users.addDebt(name, 50);
        assertEquals(50, users.getDebt(name));

        users.addDebt(name, 25);
        assertEquals(75, users.getDebt(name));

        users.subtractDebt(name, 30);
        assertEquals(45, users.getDebt(name));
    }

    // ---- resetUser ------------------------------------------------------------------

    @Test
    void resetUser_restoresTheInsertDefaults_afterMutatingEveryColumn() {
        String name = newUser("it-reset-user", "bcrypt-hash");

        users.updatePosition(name, 3, 3);
        users.updateTime(name, 10);
        users.updateCash(name, 5);
        users.updateRound(name, 4);
        users.updateJob(name, "Broker");
        users.updateClothing(name, 3);
        users.updateEat(name, 2);
        users.updateRent(name, 0);
        users.addDebt(name, 500);
        assertTrue(users.depositToBank(name, 5));

        users.resetUser(name);

        assertEquals(0, users.getXpos(name));
        assertEquals(2, users.getYpos(name));
        assertEquals(4320, users.getTime(name));
        assertEquals(100, users.getCash(name));
        assertEquals(1, users.getRound(name));
        assertEquals("Unemployed", users.getJob(name));
        assertEquals("1", users.getUserClothing(name));
        assertEquals(1, users.getRent(name));
        assertEquals(0, users.getEat(name));
        assertEquals(0, users.getDebt(name));
        assertEquals(0, users.getBank(name));
    }

    // ---- bank: atomic conditional transfers ---------------------------------------------

    @Test
    void depositToBank_movesCashToBank_andReturnsFalseWithoutChangingBalances_whenCashInsufficient() {
        String name = newUser("it-deposit-to-bank", "bcrypt-hash");

        assertTrue(users.depositToBank(name, 40));
        assertEquals(60, users.getCash(name));
        assertEquals(40, users.getBank(name));

        assertFalse(users.depositToBank(name, 1000));
        assertEquals(60, users.getCash(name));
        assertEquals(40, users.getBank(name));
    }

    @Test
    void withdrawFromBank_movesBankToCash_andReturnsFalseWithoutChangingBalances_whenBankInsufficient() {
        String name = newUser("it-withdraw-from-bank", "bcrypt-hash");
        assertTrue(users.depositToBank(name, 40));

        assertTrue(users.withdrawFromBank(name, 40));
        assertEquals(100, users.getCash(name));
        assertEquals(0, users.getBank(name));

        assertFalse(users.withdrawFromBank(name, 50));
        assertEquals(100, users.getCash(name));
        assertEquals(0, users.getBank(name));
    }

    // ---- highScores ordering ------------------------------------------------------------

    @Test
    void highScores_ordersByRoundDescending() {
        String leader = newUser("it-highscore-leader", "bcrypt-hash");
        users.updateRound(leader, 9);
        String trailer = newUser("it-highscore-trailer", "bcrypt-hash");
        users.updateRound(trailer, 2);

        List<String[]> scores = users.highScores();

        int leaderIndex = indexOfName(scores, leader);
        int trailerIndex = indexOfName(scores, trailer);
        assertTrue(leaderIndex >= 0, "leader row missing from highScores()");
        assertTrue(trailerIndex >= 0, "trailer row missing from highScores()");
        assertTrue(leaderIndex < trailerIndex, "higher round must sort before a lower one");
        assertEquals("9", scores.get(leaderIndex)[1]);
        assertEquals("2", scores.get(trailerIndex)[1]);
    }

    private static int indexOfName(List<String[]> scores, String name) {
        for (int i = 0; i < scores.size(); i++) {
            if (scores.get(i)[0].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    // ---- missing-row fallbacks, against the real DB --------------------------------------

    @Test
    void missingRowFallbacks_matchTheJdbcAdapterDefaults_forAGhostUser() {
        String ghost = "it-ghost-user-does-not-exist";

        assertEquals(0, users.getXpos(ghost));
        assertEquals(-1, users.getCash(ghost));
        assertNull(users.getJob(ghost));
        assertTrue(users.findByName(ghost).isEmpty());
    }
}
