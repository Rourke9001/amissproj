package amiss.infrastructure.persistence.jdbc;
import amiss.application.port.HelpRepository;


import amiss.application.port.PersistenceFailureException;
import java.util.Optional;

/**
 * Read-only data-access layer for the {@code tblhelp} reference table (the in-game
 * help text, keyed by topic).
 *
 * <p>Wraps the project's {@link Jdbc} helper and throws the unchecked
 * {@link PersistenceFailureException} for the caller to handle. {@code topic} is the
 * primary key, so a lookup matches at most one row and returns an {@link Optional}.
 */
public class JdbcHelpRepository implements HelpRepository {

    private final Jdbc db;

    public JdbcHelpRepository(Jdbc db) {
        this.db = db;
    }

    /** The help text for {@code topic}, or empty if there is no such topic. */
    public Optional<String> findDescription(String topic) {
        return db.queryForObject("SELECT description FROM tblhelp WHERE topic = ?",
                rs -> rs.getString("description"), topic);
    }
}
