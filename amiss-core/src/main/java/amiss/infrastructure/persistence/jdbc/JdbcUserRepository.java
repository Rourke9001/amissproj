package amiss.infrastructure.persistence.jdbc;
import amiss.application.port.UserRepository;


import amiss.domain.model.User;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data-access layer for the {@code tbluser} table (the player's saved state).
 *
 * <p>Wraps the project's {@link Jdbc} JdbcTemplate-style helper so callers work with
 * named methods instead of inline SQL. Each read returns the same fallback the game
 * has always used when no row matches; the genuine "row may be absent" lookups
 * ({@link #findByName} / {@link #findPasswordHash}) return an {@link Optional} so the
 * caller can branch on existence. Every method propagates {@link SQLException} for the
 * caller to handle, exactly as the inline calls did.
 */
public class JdbcUserRepository implements UserRepository {

    private final Jdbc db;

    public JdbcUserRepository(Jdbc db) {
        this.db = db;
    }

    // ---- reads -------------------------------------------------------------

    /** The stored BCrypt hash for {@code name}, or empty if the user does not exist. */
    public Optional<String> findPasswordHash(String name) throws SQLException {
        return db.queryForObject("SELECT password FROM tbluser WHERE name = ?",
                rs -> rs.getString("password"), name);
    }

    /** The full saved player row for {@code name}, or empty if the user does not exist. */
    public Optional<User> findByName(String name) throws SQLException {
        return db.queryForObject("SELECT * FROM tbluser WHERE name = ?",
                rs -> new User(name,
                        rs.getInt("xpos"), rs.getInt("ypos"), rs.getInt("time"),
                        rs.getInt("cash"), rs.getInt("round"), rs.getString("job"),
                        rs.getInt("clothing"), rs.getInt("rent"), rs.getInt("eat"),
                        rs.getInt("debt")),
                name);
    }

    public int getXpos(String name) throws SQLException {
        return db.queryForInt("SELECT xpos FROM tbluser WHERE name = ?", 0, name);
    }

    public int getYpos(String name) throws SQLException {
        return db.queryForInt("SELECT ypos FROM tbluser WHERE name = ?", 0, name);
    }

    public int getTime(String name) throws SQLException {
        return db.queryForInt("SELECT time FROM tbluser WHERE name = ?", -1, name);
    }

    public int getRound(String name) throws SQLException {
        return db.queryForInt("SELECT round FROM tbluser WHERE name = ?", -1, name);
    }

    public int getCash(String name) throws SQLException {
        return db.queryForInt("SELECT cash FROM tbluser WHERE name = ?", -1, name);
    }

    public int getRent(String name) throws SQLException {
        return db.queryForInt("SELECT rent FROM tbluser WHERE name = ?", -1, name);
    }

    public int getDebt(String name) throws SQLException {
        return db.queryForInt("SELECT debt FROM tbluser WHERE name = ?", -1, name);
    }

    public int getEat(String name) throws SQLException {
        return db.queryForInt("SELECT eat FROM tbluser WHERE name = ?", 0, name);
    }

    public String getJob(String name) throws SQLException {
        return db.queryForString("SELECT job FROM tbluser WHERE name = ?", null, name);
    }

    /** The player's clothing level, read as text (callers {@code parseInt} it), or null if absent. */
    public String getUserClothing(String name) throws SQLException {
        return db.queryForString("SELECT clothing FROM tbluser WHERE name = ?", null, name);
    }

    /** All players' {name, round} pairs, highest round first, for the high-score board. */
    public List<String[]> highScores() throws SQLException {
        return db.query("SELECT name, round FROM tbluser ORDER BY round DESC",
                rs -> new String[]{rs.getString("name"), Integer.toString(rs.getInt("round"))});
    }

    // ---- writes ------------------------------------------------------------

    public void updatePosition(String name, int xpos, int ypos) throws SQLException {
        db.update("UPDATE tbluser SET xpos = ?, ypos = ? WHERE name = ?", xpos, ypos, name);
    }

    public void updateTime(String name, int time) throws SQLException {
        db.update("update tbluser set time = ? WHERE name = ?", time, name);
    }

    public void updateRound(String name, int round) throws SQLException {
        db.update("update tbluser set round = ? WHERE name = ?", round, name);
    }

    public void updateCash(String name, int cash) throws SQLException {
        db.update("update tbluser SET cash = ? WHERE name = ?", cash, name);
    }

    public void updateRent(String name, int rent) throws SQLException {
        db.update("update tbluser SET rent = ? WHERE name = ?", rent, name);
    }

    public void addDebt(String name, int amount) throws SQLException {
        db.update("UPDATE tbluser SET debt = debt + ? WHERE name = ?", amount, name);
    }

    public void subtractDebt(String name, int amount) throws SQLException {
        db.update("UPDATE tbluser SET debt = debt - ? WHERE name = ?", amount, name);
    }

    public void updateJob(String name, String job) throws SQLException {
        db.update("UPDATE tbluser set job = ? WHERE name = ?", job, name);
    }

    public void updateClothing(String name, int clothing) throws SQLException {
        db.update("UPDATE tbluser SET clothing = ? WHERE name = ?", clothing, name);
    }

    public void updateEat(String name, int eat) throws SQLException {
        db.update("UPDATE tbluser SET eat = ? WHERE name = ?", eat, name);
    }

    public void updatePassword(String name, String passwordHash) throws SQLException {
        db.update("UPDATE tbluser SET password = ? WHERE name = ?", passwordHash, name);
    }

    /** Inserts a brand-new player with the game's starting values (positional INSERT). */
    public void insertNewUser(String name, String passwordHash) throws SQLException {
        db.update("INSERT INTO tbluser VALUES (?,?,0,2,72,100,1,'Unemployed',1,1,0,0)",
                name, passwordHash);
    }

    /** Resets the player's saved row to the game's starting values (the tbluser half of a game reset). */
    public void resetUser(String name) throws SQLException {
        db.update("UPDATE amissdb.tbluser SET `xpos` = 0, `ypos` = 2, `time` = 72, `cash` = 100, "
                + "`round` = 1, `job` = 'Unemployed', `clothing` = 1,`eat` = 0, `rent` = 1, `debt` = 0 "
                + "WHERE name = ?", name);
    }
}
