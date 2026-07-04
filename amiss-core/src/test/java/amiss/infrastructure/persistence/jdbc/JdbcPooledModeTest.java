package amiss.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The pooled mode added for the REST API (KAN-27): every call must borrow a
 * connection from the {@link DataSource} and give it straight back, so
 * concurrent HTTP requests never share connection state.
 */
@ExtendWith(MockitoExtension.class)
class JdbcPooledModeTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement statement;
    @Mock private ResultSet resultSet;

    @Test
    void update_borrowsAConnectionAndReturnsItToThePool() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("UPDATE tbluser SET `time` = ? WHERE name = ?")).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        Jdbc jdbc = new Jdbc(dataSource);
        int rows = jdbc.update("UPDATE tbluser SET `time` = ? WHERE name = ?", 66, "bob");

        assertEquals(1, rows);
        verify(statement).setObject(1, 66);
        verify(statement).setObject(2, "bob");
        verify(statement).close();
        verify(connection).close(); // close() on a pooled connection = return to pool
    }

    @Test
    void query_borrowsAConnectionAndReturnsItToThePool() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT `time` FROM tbluser WHERE name = ?")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(72);

        Jdbc jdbc = new Jdbc(dataSource);
        int time = jdbc.queryForInt("SELECT `time` FROM tbluser WHERE name = ?", -1, "bob");

        assertEquals(72, time);
        verify(connection).close();
    }

    @Test
    void everyCallBorrowsAFreshConnection() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("UPDATE tbluser SET rent = 0")).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);

        Jdbc jdbc = new Jdbc(dataSource);
        jdbc.update("UPDATE tbluser SET rent = 0");
        jdbc.update("UPDATE tbluser SET rent = 0");

        verify(dataSource, times(2)).getConnection();
        verify(connection, times(2)).close();
    }

    @Test
    void nullDataSourceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Jdbc((DataSource) null));
    }

    @Test
    void close_isANoOpInPooledMode() {
        // The pool's lifecycle belongs to its creator (Spring); nothing to close here.
        new Jdbc(dataSource).close();
    }
}
