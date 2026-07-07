package amiss.api.config;

import amiss.api.persistence.jpa.HelpJpaRepository;
import amiss.api.persistence.jpa.JobJpaRepository;
import amiss.api.persistence.jpa.JpaHelpRepository;
import amiss.api.persistence.jpa.JpaJobRepository;
import amiss.api.persistence.jpa.JpaUserRepository;
import amiss.api.persistence.jpa.JpaUserStatsRepository;
import amiss.api.persistence.jpa.UserJpaRepository;
import amiss.api.persistence.jpa.UserStatsJpaRepository;
import amiss.application.port.HelpRepository;
import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API's composition root — the Spring counterpart of the Swing client's
 * {@code GameContext}. Each repository port is now backed by a thin adapter over the
 * generated Spring Data interfaces (KAN-34) — {@code UserJpaRepository}, {@code
 * UserStatsJpaRepository}, {@code JobJpaRepository}, {@code HelpJpaRepository} — querying
 * entities validated against the Flyway schema (KAN-33). The pooled {@code Jdbc} bean that
 * previously lived here is gone; the API no longer touches the core's JDBC adapters. The
 * Swing client is untouched, still wiring the core's {@code Jdbc*Repository} adapters from
 * its own {@code GameContext} over its own single-connection {@code Jdbc}; services stay
 * port-typed, so the swap is invisible above this class.
 */
@Configuration
public class PersistenceConfig {

    @Bean
    UserRepository userRepository(UserJpaRepository users) {
        return new JpaUserRepository(users);
    }

    @Bean
    UserStatsRepository userStatsRepository(UserStatsJpaRepository stats, UserJpaRepository users) {
        return new JpaUserStatsRepository(stats, users);
    }

    @Bean
    JobRepository jobRepository(JobJpaRepository jobs) {
        return new JpaJobRepository(jobs);
    }

    @Bean
    HelpRepository helpRepository(HelpJpaRepository help) {
        return new JpaHelpRepository(help);
    }
}
