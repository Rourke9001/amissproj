package amiss.application.port;

import java.sql.SQLException;

/**
 * Read-only port for the {@code tbljobs} reference table (the jobs the game offers and
 * their education / salary / location / clothing requirements).
 *
 * <p>Application-layer port; the JDBC implementation is
 * {@code amiss.infrastructure.persistence.jdbc.JdbcJobRepository}. Each lookup returns the
 * game's original fallback when the job is unknown, and propagates {@link SQLException}.
 */
public interface JobRepository {

    /** Minimum education a player needs for {@code job}, or -1 if the job is unknown. */
    int getRequiredEducation(String job) throws SQLException;

    /** Hourly salary {@code job} pays, or -1 if the job is unknown. */
    int getSalary(String job) throws SQLException;

    /** The building {@code job} is worked at, or null if the job is unknown. */
    String getLocation(String job) throws SQLException;

    /** Minimum clothing level for {@code job}, read as text (callers {@code parseInt} it), or null if unknown. */
    String getRequiredClothing(String job) throws SQLException;
}
