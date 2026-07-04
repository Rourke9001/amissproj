package amiss.api.config;

import amiss.application.config.ActionCosts;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the game's cost table as a singleton {@link ActionCosts} bean, built from the
 * {@code amiss.costs.*} overrides in {@link CostsProperties} — the API-side counterpart of
 * the Swing client's {@code Config.actionCosts()}.
 */
@Configuration
@EnableConfigurationProperties(CostsProperties.class)
public class CostsConfig {

    @Bean
    ActionCosts actionCosts(CostsProperties properties) {
        return properties.toActionCosts();
    }
}
