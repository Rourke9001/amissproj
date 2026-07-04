package amiss.infrastructure.persistence.jdbc;
import amiss.infrastructure.config.Config;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin JDBC helper for the game's MySQL database.
 *
 * <p>Exposes a small, JdbcTemplate-style API: callers pass a parameterised SQL
 * string with {@code ?} placeholders plus the bind values, and never touch a
 * {@link Statement} or {@link ResultSet} themselves. Every method opens and
 * closes its own {@link PreparedStatement}/{@link ResultSet} with
 * try-with-resources, so there is no leaked cursor and no shared mutable
 * statement state. Binding the values through {@code setObject} (rather than
 * concatenating them into the SQL) is what makes the queries injection-safe.
 *
 * @author The Rourke
 */
public class Jdbc implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Jdbc.class);
    private static final String driver = "com.mysql.cj.jdbc.Driver";
    private Connection connection;

    /** Maps the current row of a {@link ResultSet} to a value of type {@code T}. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * Object which connects the SQL database
     */
    public Jdbc() {
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

    // ---- Parameterised, resource-safe API ---------------------------------

    /**
     * Runs an INSERT/UPDATE/DELETE with bind parameters.
     * @param sql    the statement, using {@code ?} for each parameter
     * @param params the values to bind, in order
     * @return the number of rows affected
     * @throws SQLException if the statement fails
     */
    public int update(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /**
     * Runs a SELECT and maps every matching row, preserving order.
     * @param sql    the query, using {@code ?} for each parameter
     * @param mapper turns the current row into a {@code T}
     * @param params the values to bind, in order
     * @return the mapped rows (empty if none matched)
     * @throws SQLException if the query fails
     */
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
                return rows;
            }
        }
    }

    /**
     * Runs a SELECT expected to match at most one row.
     * @return the mapped row, or {@link Optional#empty()} if nothing matched
     * @throws SQLException if the query fails
     */
    public <T> Optional<T> queryForObject(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Convenience for a single-column {@code int} SELECT.
     * @param defaultValue value to return if the query matched no row
     * @return the first column of the first row, or {@code defaultValue}
     * @throws SQLException if the query fails
     */
    public int queryForInt(String sql, int defaultValue, Object... params) throws SQLException {
        return queryForObject(sql, rs -> rs.getInt(1), params).orElse(defaultValue);
    }

    /**
     * Convenience for a single-column {@code String} SELECT.
     * @return the first column of the first row, or {@code defaultValue} if none matched
     * @throws SQLException if the query fails
     */
    public String queryForString(String sql, String defaultValue, Object... params) throws SQLException {
        return queryForObject(sql, rs -> rs.getString(1), params).orElse(defaultValue);
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    /** Closes the underlying connection; safe to call more than once. */
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
