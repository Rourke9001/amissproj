package amiss.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import amiss.DB;
import amiss.User;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the {@link GameServices} composition root: constructing it with a
 * {@link User} and a (mocked) {@link DB} must wire up every service. Using a mocked
 * {@code DB} means no real MySQL connection is opened — Mockito bypasses the real
 * constructor — so the wiring is verified without a database.
 */
class GameServicesTest {

    @Test
    void constructor_wiresUpEveryService() {
        User user = new User("bob", 0, 0, 720, 100, 1, "Unemployed", 1, 1, 1, 0);
        GameServices services = new GameServices(user, mock(DB.class));

        assertNotNull(services.time(), "time service");
        assertNotNull(services.education(), "education service");
        assertNotNull(services.jobs(), "job service");
        assertNotNull(services.food(), "food service");
        assertNotNull(services.stats(), "stats service");
    }
}
