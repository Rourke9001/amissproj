package amiss.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import amiss.api.error.PersistenceFailureException;
import amiss.api.error.PlayerNotFoundException;
import amiss.application.config.ActionCosts;
import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import amiss.domain.model.User;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameServicesFactoryTest {

    @Mock private UserRepository users;
    @Mock private UserStatsRepository userStats;
    @Mock private JobRepository jobs;

    private GameServicesFactory newFactory() {
        return new GameServicesFactory(users, userStats, jobs, ActionCosts.defaults());
    }

    @Test
    void forPlayer_assemblesServicesForAnExistingPlayer() throws SQLException {
        when(users.findByName("bob")).thenReturn(Optional.of(
                new User("bob", 0, 2, 72, 100, 1, "Unemployed", 1, 1, 0, 0)));

        assertNotNull(newFactory().forPlayer("bob"));
    }

    @Test
    void forPlayer_unknownPlayerThrowsPlayerNotFound() throws SQLException {
        when(users.findByName("ghost")).thenReturn(Optional.empty());

        PlayerNotFoundException ex = assertThrows(PlayerNotFoundException.class,
                () -> newFactory().forPlayer("ghost"));
        assertEquals("ghost", ex.username());
    }

    @Test
    void forPlayer_lookupFailureThrowsPersistenceFailure() throws SQLException {
        when(users.findByName("bob")).thenThrow(new SQLException("db down"));

        assertThrows(PersistenceFailureException.class, () -> newFactory().forPlayer("bob"));
    }
}
