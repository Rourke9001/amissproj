package amiss.infrastructure.persistence.jdbc;
import amiss.application.port.JobRepository;


import java.sql.SQLException;

/**
 * Read-only data-access layer for the {@code tbljobs} reference table (the jobs the
 * game offers and their education / salary / location / clothing requirements).
 *
 * <p>Wraps the project's {@link Jdbc} helper. Each lookup returns the same fallback the
 * game has always used when the job is unknown, and propagates {@link SQLException}
 * for the caller to handle, exactly as the inline calls did.
 */
public class JdbcJobRepository implements JobRepository {

    private final Jdbc db;

    public JdbcJobRepository(Jdbc db) {
        this.db = db;
    }

    /** Minimum education a player needs for {@code job}, or -1 if the job is unknown. */
    public int getRequiredEducation(String job) throws SQLException {
        return db.queryForInt("SELECT education from tbljobs where job = ?", -1, job);
    }

    /** Hourly salary {@code job} pays, or -1 if the job is unknown. */
    public int getSalary(String job) throws SQLException {
        return db.queryForInt("SELECT salary from tbljobs where job = ?", -1, job);
    }

    /** The building {@code job} is worked at, or null if the job is unknown. */
    public String getLocation(String job) throws SQLException {
        return db.queryForString("SELECT location from tbljobs where job = ?", null, job);
    }

    /** Minimum clothing level for {@code job}, read as text (callers {@code parseInt} it), or null if unknown. */
    public String getRequiredClothing(String job) throws SQLException {
        return db.queryForString("SELECT clothing from tbljobs where job = ?", null, job);
    }
}
