package amiss.api;

import amiss.api.persistence.jpa.MySqlITSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code @SpringBootTest} wiring for {@link AuthRoundTripIT} (KAN-36's acceptance test), over
 * the exact same running MySQL container {@link MySqlITSupport}'s {@code @DataJpaTest} suites
 * use — never a second one.
 *
 * <p>{@code @DataJpaTest} and a full {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} can
 * never share one cached Spring {@code ApplicationContext} (their bootstrappers configure the
 * web layer differently), so this is deliberately a second, independent context. But the
 * <em>container</em> underneath both is one and the same: referencing {@link
 * MySqlITSupport#MYSQL} below only causes the JVM to load that class — its static initializer
 * (which calls {@code MYSQL.start()}) runs at most once no matter how many classes reference
 * it, so whichever suite runs first in this JVM is the one that actually starts Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
public abstract class AuthRoundTripSupport {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlITSupport.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MySqlITSupport.MYSQL::getUsername);
        registry.add("spring.datasource.password", MySqlITSupport.MYSQL::getPassword);

        registry.add("spring.flyway.url", MySqlITSupport.MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MySqlITSupport.MYSQL::getUsername);
        registry.add("spring.flyway.password", MySqlITSupport.MYSQL::getPassword);
        // Same reasoning as MySqlITSupport: the container is schema-less on first
        // connect within this JVM run, so migrate from scratch rather than baseline.
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
    }
}
