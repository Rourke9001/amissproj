package amiss.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context smoke test: the full application wiring (persistence beans, the services factory,
 * web layer, actuator) must assemble without a database — Flyway is disabled and Hikari only
 * connects on first borrow. JPA (KAN-33) normally has Hibernate open a connection at startup
 * to validate entity mappings against the live schema ({@code ddl-auto=validate}); that would
 * defeat the point of this DB-free test, so it's turned off entirely ({@code ddl-auto=none})
 * with an explicit dialect (no connection means Hibernate can't auto-detect one) and JDBC
 * metadata access at boot disabled.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false"
})
class AmissApiApplicationTest {

    @Test
    void contextLoads() {
        // Failure mode is the context blowing up before this runs.
    }
}
