package amiss.infrastructure.config;

import amiss.application.config.ActionCosts;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application configuration.
 *
 * <p>Reads {@code /application.properties} from the classpath once at startup,
 * then lets environment variables override individual values so the same build
 * runs against different databases without rebuilding or editing source.
 * Precedence (highest first): environment variable, properties file, built-in
 * default.
 */
public final class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);
    private static final Properties PROPS = load();

    private Config() {
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = Config.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                p.load(in);
            } else {
                log.warn("application.properties not found on classpath; using env vars / defaults");
            }
        } catch (IOException e) {
            log.warn("Failed to read application.properties; using env vars / defaults", e);
        }
        return p;
    }

    /**
     * Resolves a value: environment variable {@code envKey} wins, then the
     * {@code propKey} entry in application.properties, then {@code defaultValue}.
     */
    private static String resolve(String propKey, String envKey, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return PROPS.getProperty(propKey, defaultValue);
    }

    /** JDBC URL for the game database. Override with {@code AMISS_DB_URL}. */
    public static String dbUrl() {
        return resolve("db.url", "AMISS_DB_URL",
                "jdbc:mysql://localhost:3306/amissdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    }

    /** Database user. Override with {@code AMISS_DB_USER}. */
    public static String dbUser() {
        return resolve("db.user", "AMISS_DB_USER", "amiss");
    }

    /** Database password. Override with {@code AMISS_DB_PASSWORD}. */
    public static String dbPassword() {
        return resolve("db.password", "AMISS_DB_PASSWORD", "amisspw");
    }

    /**
     * Account used only to apply Flyway migrations at startup (needs DDL on
     * amissdb, unlike the least-privilege runtime user). Override with
     * {@code AMISS_DB_MIGRATOR_USER}.
     */
    public static String dbMigratorUser() {
        return resolve("db.migrator.user", "AMISS_DB_MIGRATOR_USER", "amiss_migrator");
    }

    /** Migration account password. Override with {@code AMISS_DB_MIGRATOR_PASSWORD}. */
    public static String dbMigratorPassword() {
        return resolve("db.migrator.password", "AMISS_DB_MIGRATOR_PASSWORD", "amissmigratorpw");
    }

    /**
     * The action cost table for this deployment. Each value resolves independently:
     * env {@code AMISS_COSTS_<NAME>_MINUTES}, then {@code costs.<name>-minutes} in
     * application.properties, then the {@link ActionCosts#defaults()} value. A value that
     * is not a whole number is logged and ignored.
     */
    public static ActionCosts actionCosts() {
        ActionCosts d = ActionCosts.defaults();
        return new ActionCosts(
                resolveMinutes("work", d.workMinutes()),
                resolveMinutes("study", d.studyMinutes()),
                resolveMinutes("relax", d.relaxMinutes()),
                resolveMinutes("apply-job", d.applyJobMinutes()),
                resolveMinutes("pay-rent", d.payRentMinutes()),
                resolveMinutes("eat", d.eatMinutes()),
                resolveMinutes("shop", d.shopMinutes()),
                resolveMinutes("travel-per-step", d.travelPerStepMinutes()),
                resolveMinutes("enter-building", d.enterBuildingMinutes()),
                resolveMinutes("base-week", d.baseWeekMinutes()),
                resolveMinutes("fed-week", d.fedWeekMinutes()));
    }

    private static int resolveMinutes(String name, int defaultValue) {
        String propKey = "costs." + name + "-minutes";
        String envKey = "AMISS_COSTS_" + name.toUpperCase().replace('-', '_') + "_MINUTES";
        String raw = resolve(propKey, envKey, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Ignoring non-integer cost override {}={}", propKey, raw);
            return defaultValue;
        }
    }
}
