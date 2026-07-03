package amiss.application.port;

import amiss.domain.model.User;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Port for the {@code tbluser} table (the player's saved state).
 *
 * <p>This is an <em>application-layer port</em>: the game rules depend on this interface,
 * never on a concrete database. The JDBC implementation lives in
 * {@code amiss.infrastructure.persistence.jdbc.JdbcUserRepository}; a future Spring Data /
 * JPA adapter can replace it without touching the services. Methods propagate
 * {@link SQLException} for the caller to handle, matching the game's original behaviour.
 * ({@link SQLException} is retained here as a pragmatic interim; a later pass can map it to a
 * technology-neutral persistence exception.)
 */
public interface UserRepository {

    // ---- reads -------------------------------------------------------------

    /** The stored BCrypt hash for {@code name}, or empty if the user does not exist. */
    Optional<String> findPasswordHash(String name) throws SQLException;

    /** The full saved player row for {@code name}, or empty if the user does not exist. */
    Optional<User> findByName(String name) throws SQLException;

    int getXpos(String name) throws SQLException;

    int getYpos(String name) throws SQLException;

    int getTime(String name) throws SQLException;

    int getRound(String name) throws SQLException;

    int getCash(String name) throws SQLException;

    int getRent(String name) throws SQLException;

    int getDebt(String name) throws SQLException;

    int getEat(String name) throws SQLException;

    String getJob(String name) throws SQLException;

    /** The player's clothing level, read as text (callers {@code parseInt} it), or null if absent. */
    String getUserClothing(String name) throws SQLException;

    /** All players' {name, round} pairs, highest round first, for the high-score board. */
    List<String[]> highScores() throws SQLException;

    // ---- writes ------------------------------------------------------------

    void updatePosition(String name, int xpos, int ypos) throws SQLException;

    void updateTime(String name, int time) throws SQLException;

    void updateRound(String name, int round) throws SQLException;

    void updateCash(String name, int cash) throws SQLException;

    void updateRent(String name, int rent) throws SQLException;

    void addDebt(String name, int amount) throws SQLException;

    void subtractDebt(String name, int amount) throws SQLException;

    void updateJob(String name, String job) throws SQLException;

    void updateClothing(String name, int clothing) throws SQLException;

    void updateEat(String name, int eat) throws SQLException;

    void updatePassword(String name, String passwordHash) throws SQLException;

    /** Inserts a brand-new player with the game's starting values. */
    void insertNewUser(String name, String passwordHash) throws SQLException;

    /** Resets the player's saved row to the game's starting values. */
    void resetUser(String name) throws SQLException;
}
