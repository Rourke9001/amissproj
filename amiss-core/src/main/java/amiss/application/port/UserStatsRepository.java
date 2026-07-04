package amiss.application.port;

import java.sql.SQLException;

/**
 * Port for the {@code tbluserstats} table (a player's happiness, education, work
 * experience and current study progress).
 *
 * <p>Application-layer port; the JDBC implementation is
 * {@code amiss.infrastructure.persistence.jdbc.JdbcUserStatsRepository}. Reads return the
 * game's original fallbacks; writes propagate {@link SQLException} for the caller.
 */
public interface UserStatsRepository {

    // ---- reads -------------------------------------------------------------

    int getEducation(String name) throws SQLException;

    int getEduprog(String name) throws SQLException;

    /** Work experience, read as text (the game displays it as a string), or null if absent. */
    String getWork(String name) throws SQLException;

    /** Happiness, read as text (the game displays it as a string), or null if absent. */
    String getHappiness(String name) throws SQLException;

    // ---- writes ------------------------------------------------------------

    void updateEducation(String name, int education) throws SQLException;

    void updateEduprog(String name, int eduprog) throws SQLException;

    void incrementWork(String name) throws SQLException;

    void incrementHappiness(String name) throws SQLException;

    /** Inserts the starting (all-zero) stats row for a brand-new player. */
    void insertNewStats(String name) throws SQLException;

    /** Resets happiness/education/work to 0 (leaves eduprog). */
    void resetStats(String name) throws SQLException;
}
