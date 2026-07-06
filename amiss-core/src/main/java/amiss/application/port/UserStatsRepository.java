package amiss.application.port;

/**
 * Port for the {@code tbluserstats} table (a player's happiness, education, work
 * experience and current study progress).
 *
 * <p>Application-layer port; the JDBC implementation is
 * {@code amiss.infrastructure.persistence.jdbc.JdbcUserStatsRepository}. Reads return the
 * game's original fallbacks; writes throw the unchecked {@link PersistenceFailureException}
 * for the caller.
 */
public interface UserStatsRepository {

    // ---- reads -------------------------------------------------------------

    int getEducation(String name);

    int getEduprog(String name);

    /** Work experience, read as text (the game displays it as a string), or null if absent. */
    String getWork(String name);

    /** Happiness, read as text (the game displays it as a string), or null if absent. */
    String getHappiness(String name);

    // ---- writes ------------------------------------------------------------

    void updateEducation(String name, int education);

    void updateEduprog(String name, int eduprog);

    void incrementWork(String name);

    void incrementHappiness(String name);

    /** Inserts the starting (all-zero) stats row for a brand-new player. */
    void insertNewStats(String name);

    /** Resets happiness/education/work to 0 (leaves eduprog). */
    void resetStats(String name);
}
