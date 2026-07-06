package amiss.infrastructure.persistence.jdbc;
import amiss.application.port.UserStatsRepository;


import amiss.application.port.PersistenceFailureException;

/**
 * Data-access layer for the {@code tbluserstats} table (a player's happiness,
 * education, work experience and current study progress).
 *
 * <p>Wraps the project's {@link Jdbc} helper. Reads return the same fallback the game
 * has always used when no row matches; writes throw the unchecked
 * {@link PersistenceFailureException} for the caller to handle, exactly as the inline
 * calls did.
 */
public class JdbcUserStatsRepository implements UserStatsRepository {

    private final Jdbc db;

    public JdbcUserStatsRepository(Jdbc db) {
        this.db = db;
    }

    // ---- reads -------------------------------------------------------------

    public int getEducation(String name) {
        return db.queryForInt("SELECT education from tbluserstats where name = ?", -1, name);
    }

    public int getEduprog(String name) {
        return db.queryForInt("SELECT eduprog from tbluserstats where name = ?", -1, name);
    }

    /** Work experience, read as text (the game displays it as a string), or null if absent. */
    public String getWork(String name) {
        return db.queryForString("SELECT work FROM tbluserstats WHERE name = ?", null, name);
    }

    /** Happiness, read as text (the game displays it as a string), or null if absent. */
    public String getHappiness(String name) {
        return db.queryForString("SELECT happiness FROM tbluserstats WHERE name = ?", null, name);
    }

    // ---- writes ------------------------------------------------------------

    public void updateEducation(String name, int education) {
        db.update("UPDATE tbluserstats set education = ? where name = ?", education, name);
    }

    public void updateEduprog(String name, int eduprog) {
        db.update("Update tbluserstats set eduprog = ? where name = ?", eduprog, name);
    }

    public void incrementWork(String name) {
        db.update("UPDATE tbluserstats SET work = work + ? WHERE name = ?", 1, name);
    }

    public void incrementHappiness(String name) {
        db.update("UPDATE tbluserstats SET happiness = happiness + ? WHERE name = ?", 1, name);
    }

    /** Inserts the starting (all-zero) stats row for a brand-new player (positional INSERT). */
    public void insertNewStats(String name) {
        db.update("INSERT INTO tbluserstats VALUES (?,0,0,0,0)", name);
    }

    /** Resets happiness/education/work to 0 (the tbluserstats half of a game reset; leaves eduprog). */
    public void resetStats(String name) {
        db.update("UPDATE amissdb.tbluserstats SET `happiness` = 0, `education` = 0, `work` = 0 WHERE name = ?",
                name);
    }
}
