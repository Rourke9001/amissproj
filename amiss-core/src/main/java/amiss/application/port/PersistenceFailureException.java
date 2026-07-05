package amiss.application.port;

/**
 * Technology-neutral persistence failure signal thrown by port implementations.
 *
 * <p>Replaces the former checked {@code SQLException} (from {@code java.sql}) on the port
 * contracts ({@link UserRepository}, {@link UserStatsRepository}, {@link JobRepository},
 * {@link HelpRepository}): callers depend on this single unchecked exception rather than
 * a JDBC type, so a future non-JDBC adapter (e.g. Spring Data / JPA) can implement the
 * same ports without leaking its own persistence technology into the application layer.
 * JDBC adapters translate the checked exception into this one at the boundary
 * (see {@code amiss.infrastructure.persistence.jdbc.Jdbc}).
 */
public class PersistenceFailureException extends RuntimeException {

    public PersistenceFailureException(String message) {
        super(message);
    }

    public PersistenceFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceFailureException(Throwable cause) {
        super(cause);
    }
}
