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
 * Shared Testcontainers / {@code @DataJpaTest} base for the KAN-35 {@code *IT} classes:
 * the JPA port adapters ({@link PersistenceConfig}'s beans) run against a real MySQL
 * container that Flyway migrates from scratch (V1 -> V4) with {@code ddl-auto=validate}
 * still effective — a green boot here is itself the no-schema-drift proof.
 *
 * <p>{@code disabledWithoutDocker = true} is the only Docker gate (no Maven profile):
 * a Docker-less {@code mvnw verify} <em>skips</em> every subclass test; CI runs them
 * for real. The image is pinned to {@code mysql:9} because Testcontainers' default tag
 * is older than the MySQL 9.7 production runs. Both {@code spring.datasource.*} AND
 * {@code spring.flyway.*} are pointed at the container by hand — {@code application.yml}
 * sets explicit {@code spring.flyway.*} defaults ({@code amiss_migrator}) that would
 * otherwise win over auto-detection.
 *
 * <p>{@code withConfigurationOverride} mounts {@code testcontainers-mysql-conf} (a
 * near-empty {@code my.cnf}) in place of the config {@code MySQLContainer} bundles: that
 * one sets MySQL 5.x/8.0-era knobs including {@code innodb_log_file_size}, removed in
 * MySQL 9 — left in place, the container fails to boot.
 *
 * <p><b>Transaction trap:</b> plain {@code @DataJpaTest} wraps each test in one rollback
 * transaction with a shared {@code EntityManager}, whose first-level cache would mask the
 * adapters' {@code @Modifying} UPDATEs from later reads — false results, not real proof.
 * {@code @Transactional(propagation = NOT_SUPPORTED)} suspends that outer transaction
 * (the documented off-switch), so every adapter call commits its own transaction exactly
 * as in production. Consequence: no free rollback — every subclass must delete the rows
 * it inserted in an {@code @AfterEach} and never touch the Flyway-seeded
 * {@code tbljobs}/{@code tblhelp} reference rows.
 *
 * <p><b>Singleton container, not {@code @Container}:</b> all subclasses share one cached
 * Spring context (and connection pool). {@code @Container} would stop/restart the
 * container between subclasses while the cached context's {@code DataSource} still
 * points at the dead container's port — "Communications link failure" everywhere after
 * the first subclass. Starting it once in a static initializer and never stopping it
 * (Ryuk reaps it at JVM exit) is the documented Testcontainers singleton pattern.
 *
 * <p>{@code MYSQL} is {@code public} so KAN-36's {@code @SpringBootTest} slice
 * ({@code amiss.api.AuthRoundTripSupport}) can reuse the same running container — the
 * two bootstrappers can never share a cached context, but must share one container.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class MySqlITSupport {

    public static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:9"))
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
