package amiss.infrastructure.persistence.flyway;

import amiss.infrastructure.config.Config;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the versioned schema migrations ({@code classpath:db/migration}) on
 * startup, before the game opens its JDBC connection.
 *
 * <p>Runs as the dedicated {@code amiss_migrator} account (created by
 * {@code db/bootstrap.sql}), which holds the DDL rights that migrations need;
 * the game itself keeps playing as the least-privilege {@code amiss} user.
 *
 * <p>Databases that predate Flyway already contain the V1 tables, so
 * {@code baselineOnMigrate}/{@code baselineVersion=1} marks V1 as applied
 * there instead of re-running it — saved players are untouched and only newer
 * migrations apply.
 *
 * <p>A migration failure is logged but not rethrown, mirroring how a failed
 * DB connection is handled: the login window still opens and the console
 * carries the diagnosis.
 */
public final class FlywayMigrator {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrator.class);

    private FlywayMigrator() {
    }

    /** Migrates the schema to the latest version; logs (never throws) on failure. */
    public static void migrate() {
        try {
            MigrateResult result = Flyway.configure()
                    .dataSource(Config.dbUrl(), Config.dbMigratorUser(), Config.dbMigratorPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .load()
                    .migrate();
            log.info("Database schema up to date ({} migration(s) applied)",
                    result.migrationsExecuted);
        } catch (FlywayException e) {
            log.error("Schema migration failed - the game may be running against an outdated "
                    + "schema. Re-run db/bootstrap.sql as root to (re)create the amiss_migrator "
                    + "account, or check AMISS_DB_MIGRATOR_USER / AMISS_DB_MIGRATOR_PASSWORD.", e);
        }
    }
}
