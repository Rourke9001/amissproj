package amiss.api.error;

import java.sql.SQLException;

/**
 * Unchecked wrapper for a {@link SQLException} crossing the REST boundary.
 * The core ports deliberately still declare {@code throws SQLException} (see
 * docs/ARCHITECTURE.md "known interim simplifications"); the API translates it
 * here so controllers stay exception-clean and the advice can map it to a
 * single 500 Problem Details response.
 */
public class PersistenceFailureException extends RuntimeException {

    public PersistenceFailureException(SQLException cause) {
        super("Database access failed", cause);
    }
}
