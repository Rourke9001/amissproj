package amiss.infrastructure.persistence.jdbc;
import amiss.application.port.PersistenceFailureException;
import amiss.infrastructure.config.Config;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin JDBC helper for the game's MySQL database.
 *
 * <p>Exposes a small, JdbcTemplate-style API: callers pass parameterised SQL with
 * {@code ?} placeholders plus bind values, never touching a {@link Statement} or
 * {@link ResultSet} directly. Every method opens/closes its own
 * {@link PreparedStatement}/{@link ResultSet} via try-with-resources (no leaked cursors,
 * no shared mutable state), and binds values through {@code setObject} rather than
 * concatenating them, which is what keeps the queries injection-safe.
 *
 * <p>Two connection modes: {@link #Jdbc()} is the Swing client's — one connection opened
 * from {@link Config} and held for the app's life (single-threaded UI) — while
 * {@link #Jdbc(DataSource)} is the REST API's — a connection borrowed from the pool per
 * call and returned immediately, so concurrent requests never share connection state.
 *
 * @author The Rourke
 */
public class Jdbc implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Jdbc.class);
    private static final String driver = "com.mysql.cj.jdbc.Driver";
    private final DataSource dataSource;
    private Connection connection;

    /** Maps the current row of a {@link ResultSet} to a value of type {@code T}. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** Work that needs a live connection; used to abstract over the two modes. */
    @FunctionalInterface
    private interface ConnectionWork<T> {
        T run(Connection con) throws SQLException;
    }

    /**
     * Single-connection mode: connects once using the {@link Config} settings.
     * Used by the Swing client, whose UI thread is the only caller.
     */
    public Jdbc() {
        this.dataSource = null;
        try {
            Class.forName(driver);
            connection = DriverManager.getConnection(Config.dbUrl(), Config.dbUser(), Config.dbPassword());
            log.info("Connection Successful");
        } catch (SQLException s) {
            log.error("Cannot connect to database: {}", s.getMessage());
        } catch (ClassNotFoundException c) {
            log.error("Cannot load driver", c);
        }
    }

    /**
     * Pooled mode: every call borrows a connection from {@code dataSource} and
     * returns it when done, making the helper safe under concurrent callers.
     * The pool's lifecycle belongs to whoever created it (e.g. Spring).
     *
     * @param dataSource the connection pool to borrow from; never {@code null}
     */
    public Jdbc(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        this.dataSource = dataSource;
    }

    /** Runs {@code work} with a connection appropriate to the mode. */
    private <T> T withConnection(ConnectionWork<T> work) throws SQLException {
        if (dataSource != null) {
            try (Connection con = dataSource.getConnection()) {
                return work.run(con);
            }
        }
        return work.run(connection);
    }

    // ---- Parameterised, resource-safe API ---------------------------------

    /**
     * Runs an INSERT/UPDATE/DELETE with bind parameters.
     * @param sql    the statement, using {@code ?} for each parameter
     * @param params the values to bind, in order
     * @return the number of rows affected
     * @throws PersistenceFailureException if the statement fails
     */
    public int update(String sql, Object... params) {
        try {
            return withConnection(con -> {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    bind(ps, params);
                    return ps.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw new PersistenceFailureException(e);
        }
    }

    /**
     * Runs a SELECT and maps every matching row, preserving order.
     * @param sql    the query, using {@code ?} for each parameter
     * @param mapper turns the current row into a {@code T}
     * @param params the values to bind, in order
     * @return the mapped rows (empty if none matched)
     * @throws PersistenceFailureException if the query fails
     */
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try {
            return withConnection(con -> {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    bind(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        List<T> rows = new ArrayList<>();
                        while (rs.next()) {
                            rows.add(mapper.map(rs));
                        }
                        return rows;
                    }
                }
            });
        } catch (SQLException e) {
            throw new PersistenceFailureException(e);
        }
    }

    /**
     * Runs a SELECT expected to match at most one row.
     * @return the mapped row, or {@link Optional#empty()} if nothing matched
     * @throws PersistenceFailureException if the query fails
     */
    public <T> Optional<T> queryForObject(String sql, RowMapper<T> mapper, Object... params) {
        try {
            return withConnection(con -> {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    bind(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? Optional.ofNullable(mapper.map(rs)) : Optional.empty();
                    }
                }
            });
        } catch (SQLException e) {
            throw new PersistenceFailureException(e);
        }
    }

    /**
     * Convenience for a single-column {@code int} SELECT.
     * @param defaultValue value to return if the query matched no row
     * @return the first column of the first row, or {@code defaultValue}
     * @throws PersistenceFailureException if the query fails
     */
    public int queryForInt(String sql, int defaultValue, Object... params) {
        return queryForObject(sql, rs -> rs.getInt(1), params).orElse(defaultValue);
    }

    /**
     * Convenience for a single-column {@code String} SELECT.
     * @return the first column of the first row, or {@code defaultValue} if none matched
     * @throws PersistenceFailureException if the query fails
     */
    public String queryForString(String sql, String defaultValue, Object... params) {
        return queryForObject(sql, rs -> rs.getString(1), params).orElse(defaultValue);
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    /**
     * Closes the single-connection mode's connection; safe to call more than
     * once. In pooled mode this is a no-op — the pool owns its connections.
     */
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to close database connection", e);
        }
    }
}
