package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import amiss.domain.model.User;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the {@link GameServices} wiring: constructing it with a {@link User} and
 * (mocked) repository ports must wire up every service. Mocking the ports means no real
 * MySQL connection is opened, so the wiring is verified without a database.
 */
class GameServicesTest {

    @Test
    void constructor_wiresUpEveryService() {
        User user = new User("bob", 0, 0, 720, 100, 1, "Unemployed", 1, 1, 1, 0);
        GameServices services = new GameServices(user,
                mock(UserRepository.class), mock(UserStatsRepository.class), mock(JobRepository.class));

        assertNotNull(services.time(), "time service");
        assertNotNull(services.education(), "education service");
        assertNotNull(services.jobs(), "job service");
        assertNotNull(services.food(), "food service");
        assertNotNull(services.stats(), "stats service");
        assertNotNull(services.turn(), "turn service");
        assertNotNull(services.travel(), "travel service");
        assertNotNull(services.bank(), "bank service");
        assertNotNull(services.rent(), "rent service");
    }
}
