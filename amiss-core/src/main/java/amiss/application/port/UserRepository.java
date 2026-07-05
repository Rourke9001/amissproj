package amiss.application.port;

import amiss.domain.model.User;
import java.util.List;
import java.util.Optional;

/**
 * Port for the {@code tbluser} table (the player's saved state).
 *
 * <p>This is an <em>application-layer port</em>: the game rules depend on this interface,
 * never on a concrete database. The JDBC implementation lives in
 * {@code amiss.infrastructure.persistence.jdbc.JdbcUserRepository}; a future Spring Data /
 * JPA adapter can replace it without touching the services. Methods throw the unchecked
 * {@link PersistenceFailureException} for the caller to handle, so no JDBC type leaks into
 * this interface.
 */
public interface UserRepository {

    // ---- reads -------------------------------------------------------------

    /** The stored BCrypt hash for {@code name}, or empty if the user does not exist. */
    Optional<String> findPasswordHash(String name);

    /** The full saved player row for {@code name}, or empty if the user does not exist. */
    Optional<User> findByName(String name);

    int getXpos(String name);

    int getYpos(String name);

    int getTime(String name);

    int getRound(String name);

    int getCash(String name);

    int getRent(String name);

    int getDebt(String name);

    int getEat(String name);

    String getJob(String name);

    /** The player's clothing level, read as text (callers {@code parseInt} it), or null if absent. */
    String getUserClothing(String name);

    /** All players' {name, round} pairs, highest round first, for the high-score board. */
    List<String[]> highScores();

    /** The player's savings balance, or -1 if the player does not exist. */
    int getBank(String name);

    // ---- writes ------------------------------------------------------------

    void updatePosition(String name, int xpos, int ypos);

    void updateTime(String name, int time);

    void updateRound(String name, int round);

    void updateCash(String name, int cash);

    void updateRent(String name, int rent);

    void addDebt(String name, int amount);

    void subtractDebt(String name, int amount);

    void updateJob(String name, String job);

    void updateClothing(String name, int clothing);

    void updateEat(String name, int eat);

    void updatePassword(String name, String passwordHash);

    /** Inserts a brand-new player with the game's starting values. */
    void insertNewUser(String name, String passwordHash);

    /** Resets the player's saved row to the game's starting values. */
    void resetUser(String name);

    /**
     * Moves {@code amount} from cash to bank in one atomic conditional update.
     * @return {@code false} if the player doesn't have {@code amount} in cash (nothing changed)
     */
    boolean depositToBank(String name, int amount);

    /**
     * Moves {@code amount} from bank to cash in one atomic conditional update.
     * @return {@code false} if the player doesn't have {@code amount} in the bank (nothing changed)
     */
    boolean withdrawFromBank(String name, int amount);
}
