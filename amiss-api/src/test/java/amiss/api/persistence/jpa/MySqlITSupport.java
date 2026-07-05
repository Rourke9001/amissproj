package amiss.api.persistence.jpa;

import amiss.api.config.PersistenceConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers / {@code @DataJpaTest} wiring for the KAN-35 integration
 * tests. Every {@code *IT} class in this package extends this base to exercise the
 * four JPA port adapters ({@link PersistenceConfig}'s beans) against a real MySQL
 * container that Flyway migrates from scratch (V1 -> V4) — the same schema Boot
 * applies in production, never Hibernate DDL ({@code ddl-auto=validate} stays
 * effective, so a green boot here is itself the no-schema-drift proof).
 *
 * <p>{@code disabledWithoutDocker = true} on {@link Testcontainers} is the only
 * Docker gate — there is no Maven profile. A Docker-less {@code mvnw verify} still
 * invokes Failsafe, but every {@code @Test} in a subclass is <em>skipped</em>, not
 * failed; CI (ubuntu-latest, Docker preinstalled) always runs them for real.
 *
 * <p>The image is pinned to {@code mysql:9} via {@link DockerImageName#parse}:
 * Testcontainers' un-pinned {@code MySQLContainer} default tag is older than the
 * MySQL 9.7 the app runs against in production. {@code spring.datasource.*} AND
 * {@code spring.flyway.*} both need the container's URL/credentials —
 * {@code application.yml} sets explicit {@code spring.flyway.*} defaults (the
 * {@code amiss_migrator} account) that would otherwise win over a
 * {@code @ServiceConnection}-style auto-detection, so both property groups are set
 * by hand here instead of relying on that.
 *
 * <p>{@code withConfigurationOverride} points at {@code testcontainers-mysql-conf} (a
 * near-empty {@code my.cnf} on the test classpath) to neutralize a bundled config file
 * {@code MySQLContainer} always mounts otherwise: it sets startup tuning knobs from the
 * MySQL 5.x/8.0 era, including {@code innodb_log_file_size}, a variable MySQL 9 removed
 * — left in place, the container fails to boot at all against {@code mysql:9}.
 *
 * <p><b>Transaction trap (read before touching this class):</b> plain
 * {@code @DataJpaTest} wraps every {@code @Test} in one rollback transaction shared
 * by a single {@code EntityManager}. The port adapters' own {@code @Transactional}
 * would simply join that outer transaction, and the shared persistence context's
 * first-level cache would then mask the adapters' {@code @Modifying} UPDATEs from
 * a subsequent read in the same test — stale-cache false passes/failures, not real
 * proof the adapters work. {@code @Transactional(propagation = NOT_SUPPORTED)}
 * below suspends {@code @DataJpaTest}'s outer transaction (the documented way to
 * disable it — see the {@code @DataJpaTest} javadoc), so every adapter call opens
 * and commits its own transaction exactly as it does in production (one port call =
 * one transaction, JDBC-autocommit parity). The consequence: there is no free
 * rollback here. Every IT subclass must delete the rows <em>it</em> inserted, in an
 * {@code @AfterEach}, and must never touch the Flyway-seeded {@code tbljobs} /
 * {@code tblhelp} reference rows.
 *
 * <p><b>Singleton container, not {@code @Container}:</b> every subclass shares this
 * identical {@code @DataJpaTest} configuration, so Spring's test-context cache reuses
 * one {@link org.springframework.context.ApplicationContext} (and its one
 * connection pool) across all of them — this is Spring's normal, desirable caching
 * behaviour. Annotating {@code MYSQL} with {@code @Container} would fight that: JUnit
 * would stop the shared container after each subclass's tests and (try to) restart it
 * for the next one, while the cached Spring context's already-built {@code DataSource}
 * keeps pointing at the now-dead container's old port — every test in every subclass
 * after the first fails with "Communications link failure". Starting the container
 * once here, in a static initializer, and never stopping it (Ryuk reaps it at JVM
 * exit) matches the container's lifecycle to the context's and sidesteps that
 * entirely — the documented Testcontainers "singleton container" pattern.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class MySqlITSupport {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:9"))
            .withConfigurationOverride("testcontainers-mysql-conf");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        // The container is always schema-less on first connect: migrate V1->V4 from
        // scratch rather than baseline (application.yml's baseline-on-migrate=true is
        // for pre-Flyway production installs, never a fresh container).
        registry.add("spring.flyway.baseline-on-migrate", () -> false);
    }
}
