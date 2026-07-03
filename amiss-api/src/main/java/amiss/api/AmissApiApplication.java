package amiss.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the AmissProj REST API — the web counterpart of
 * the Swing client's {@code LoginGUI}. Startup order mirrors the desktop app:
 * Flyway (auto-configured, running as the {@code amiss_migrator} account)
 * brings the schema up to date before the app serves traffic, while the
 * runtime {@code DataSource} stays on the least-privilege {@code amiss} user.
 */
@SpringBootApplication
public class AmissApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmissApiApplication.class, args);
    }
}
