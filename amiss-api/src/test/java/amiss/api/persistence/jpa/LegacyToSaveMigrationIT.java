package amiss.api.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import amiss.infrastructure.security.PasswordHasher;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * KAN-54: proves the full V1 -&gt; V6 migration path against a genuine pre-V5
 * account — registered back when {@code tbluser} carried all the game state
 * and {@code tbluserstats} existed — survives V5's expand (copy into {@code
 * tblsave}) and V6's contract (drop the old columns/tables) with its save
 * state intact.
 *
 * <p>This needs two different migration targets in one test (stop at V4,
 * seed the legacy row, then run the rest), so it drives the plain Flyway API
 * directly rather than going through a Spring context: no {@code @DataJpaTest}
 * / {@code @SpringBootTest} bootstraps here, unlike every other {@code *IT} in
 * this package.
 *
 * <p><b>Own container, not {@link MySqlITSupport#MYSQL}:</b> that singleton is
 * shared by every Spring-based {@code *IT} class and is always driven straight
 * to the latest version at context startup, with no way to know whether some
 * other class has already migrated it past V4 by the time this class runs.
 * Targeting V4 against an already-latest schema would silently no-op instead
 * of stopping where this test needs it to, so this class starts its own
 * MySQL 9 container — schema-less until this class's {@link #migrateALegacyAccountThroughTheFullPath()}
 * runs — and never shares it. Same singleton idiom otherwise (start once in a
 * static initializer, let Testcontainers' Ryuk reap it at JVM exit).
 *
 * <p>The migration runs once for the whole class ({@code @BeforeAll}, JUnit's
 * default per-method lifecycle requires it to be static); each {@code @Test}
 * below only reads the resulting schema, so a failure in one assertion still
 * reports the others independently.
 */
@Testcontainers(disabledWithoutDocker = true)
class LegacyToSaveMigrationIT {

    private static final String LEGACY_USER = "prev5user";

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:9"))
            .withConfigurationOverride("testcontainers-mysql-conf");

    static {
        MYSQL.start();
    }

    @BeforeAll
    static void migrateALegacyAccountThroughTheFullPath() throws SQLException {
        // 1) pre-V5 shape only: tbluser still carries game state, tbluserstats exists.
        migrate(MigrationVersion.fromVersion("4"));

        try (Connection conn = connect()) {
            seedLegacyUser(conn);
        }

        // 2) the rest of the way: V5 (expand/copy into tblsave) then V6 (contract/drop legacy shapes).
        migrate(MigrationVersion.LATEST);
    }

    private static void migrate(MigrationVersion target) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(target);
        configuration.load().migrate();
    }

    /** Seeds a legacy tbluser + tbluserstats pair exactly the way pre-V5 registration did. */
    private static void seedLegacyUser(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tbluser (name, password, xpos, ypos, `time`, round, cash,
                                     bank, debt, rent, eat, clothing, job)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, LEGACY_USER);
            ps.setString(2, PasswordHasher.hash("prev5-password"));
            ps.setInt(3, 5);           // xpos
            ps.setInt(4, 3);           // ypos
            ps.setInt(5, 2160);        // time: minutes left this round
            ps.setInt(6, 6);           // round
            ps.setInt(7, 875);         // cash
            ps.setInt(8, 300);         // bank
            ps.setInt(9, 50);          // debt
            ps.setInt(10, 0);          // rent: paid
            ps.setInt(11, 3);          // eat: weeks of food stored
            ps.setInt(12, 2);          // clothing: Dress
            ps.setString(13, "Secretary"); // legacy free-text job title
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO tbluserstats (name, happiness, education, work, eduprog) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, LEGACY_USER);
            ps.setInt(2, 68); // happiness
            ps.setInt(3, 2);  // education
            ps.setInt(4, 18); // work
            ps.setInt(5, 1);  // eduprog
            ps.executeUpdate();
        }
    }

    // ===== assertions =====

    @Test
    void copiesTheLegacyAccountIntoOneStarterSaveCarryingItsState() throws SQLException {
        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM tblsave WHERE owner = ?")) {
            ps.setString(1, LEGACY_USER);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("tblsave row for the migrated legacy user").isTrue();

                assertThat(rs.getString("label")).isEqualTo("Save 1");
                // carried over verbatim from tbluser/tbluserstats:
                assertThat(rs.getInt("xpos")).isEqualTo(5);
                assertThat(rs.getInt("ypos")).isEqualTo(3);
                assertThat(rs.getInt("time")).isEqualTo(2160);
                assertThat(rs.getInt("round")).isEqualTo(6);
                assertThat(rs.getInt("cash")).isEqualTo(875);
                assertThat(rs.getInt("bank")).isEqualTo(300);
                assertThat(rs.getInt("debt")).isEqualTo(50);
                assertThat(rs.getInt("rent")).isEqualTo(0);
                assertThat(rs.getInt("eat")).isEqualTo(3);
                assertThat(rs.getInt("clothing")).isEqualTo(2);
                assertThat(rs.getInt("happiness")).isEqualTo(68);
                // reset rather than carried over (spec: career/education don't map onto the
                // real catalog, V5's migration note):
                assertThat(rs.getObject("job_id")).isNull();
                assertThat(rs.getObject("current_course_id")).isNull();
                assertThat(rs.getInt("experience")).isEqualTo(10);
                assertThat(rs.getInt("dependability")).isEqualTo(20);
                assertThat(rs.getInt("goal_wealth")).isEqualTo(50);
                assertThat(rs.getInt("goal_happiness")).isEqualTo(50);
                assertThat(rs.getInt("goal_education")).isEqualTo(50);
                assertThat(rs.getInt("goal_career")).isEqualTo(50);

                assertThat(rs.next()).as("exactly one starter save").isFalse();
            }
        }
    }

    @Test
    void tbluserSurvivesV6ShrunkToCredentialsOnly() throws SQLException {
        try (Connection conn = connect()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, password FROM tbluser WHERE name = ?")) {
                ps.setString(1, LEGACY_USER);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("tbluser row survives V6").isTrue();
                    assertThat(rs.getString("name")).isEqualTo(LEGACY_USER);
                    assertThat(PasswordHasher.matches("prev5-password", rs.getString("password"))).isTrue();
                }
            }
            assertThat(columnCount(conn, "tbluser"))
                    .as("tbluser has only name+password left")
                    .isEqualTo(2);
        }
    }

    @Test
    void v6DropsTheLegacyTablesButLeavesTblhelpAlone() throws SQLException {
        try (Connection conn = connect()) {
            assertThat(tableExists(conn, "tbluserstats")).isFalse();
            assertThat(tableExists(conn, "tbljobs")).isFalse();
            assertThat(tableExists(conn, "tblhelp")).isTrue();
        }
    }

    // ===== helpers =====

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static int columnCount(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }
}
