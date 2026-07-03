package amiss.api.config;

import amiss.application.port.HelpRepository;
import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import amiss.infrastructure.persistence.jdbc.Jdbc;
import amiss.infrastructure.persistence.jdbc.JdbcHelpRepository;
import amiss.infrastructure.persistence.jdbc.JdbcJobRepository;
import amiss.infrastructure.persistence.jdbc.JdbcUserRepository;
import amiss.infrastructure.persistence.jdbc.JdbcUserStatsRepository;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API's composition root — the Spring counterpart of the Swing client's
 * {@code GameContext}. Wires the existing core adapters as beans: {@link Jdbc}
 * runs in pooled mode over Boot's auto-configured Hikari {@link DataSource}
 * (the least-privilege {@code amiss} account), and each repository port gets
 * its JDBC adapter. The services stay port-typed, so swapping this layer for
 * Spring Data JPA later (KAN-17) touches nothing above it.
 */
@Configuration
public class PersistenceConfig {

    @Bean
    Jdbc jdbc(DataSource dataSource) {
        return new Jdbc(dataSource);
    }

    @Bean
    UserRepository userRepository(Jdbc jdbc) {
        return new JdbcUserRepository(jdbc);
    }

    @Bean
    UserStatsRepository userStatsRepository(Jdbc jdbc) {
        return new JdbcUserStatsRepository(jdbc);
    }

    @Bean
    JobRepository jobRepository(Jdbc jdbc) {
        return new JdbcJobRepository(jdbc);
    }

    @Bean
    HelpRepository helpRepository(Jdbc jdbc) {
        return new JdbcHelpRepository(jdbc);
    }
}
