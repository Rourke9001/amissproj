package amiss.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context smoke test: the full application wiring (persistence beans, the
 * services factory, web layer, actuator) must assemble without a database —
 * Flyway is disabled here and Hikari only connects on first borrow.
 */
@SpringBootTest(properties = "spring.flyway.enabled=false")
class AmissApiApplicationTest {

    @Test
    void contextLoads() {
        // Failure mode is the context blowing up before this runs.
    }
}
