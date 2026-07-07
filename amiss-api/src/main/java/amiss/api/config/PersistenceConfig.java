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
 * The API's composition root. Each repository port is backed by a thin adapter over the
 * generated Spring Data interfaces (KAN-34) — {@code UserJpaRepository}, {@code
 * UserStatsJpaRepository}, {@code JobJpaRepository}, {@code HelpJpaRepository} — querying
 * entities validated against the Flyway schema (KAN-33). Services stay port-typed, so a
 * persistence swap is invisible above this class. (The Swing client and the core's JDBC
 * adapters this class once mirrored were retired with KAN-51.)
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
