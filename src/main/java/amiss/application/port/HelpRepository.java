package amiss.application.port;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Read-only port for the {@code tblhelp} reference table (the in-game help text, keyed by
 * topic).
 *
 * <p>Application-layer port; the JDBC implementation is
 * {@code amiss.infrastructure.persistence.jdbc.JdbcHelpRepository}. {@code topic} is the
 * primary key, so a lookup matches at most one row and returns an {@link Optional}.
 */
public interface HelpRepository {

    /** The help text for {@code topic}, or empty if there is no such topic. */
    Optional<String> findDescription(String topic) throws SQLException;
}
