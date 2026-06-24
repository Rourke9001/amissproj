package amiss;

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
        return resolve("db.user", "AMISS_DB_USER", "root");
    }

    /** Database password. Override with {@code AMISS_DB_PASSWORD}. */
    public static String dbPassword() {
        return resolve("db.password", "AMISS_DB_PASSWORD", "password");
    }
}
