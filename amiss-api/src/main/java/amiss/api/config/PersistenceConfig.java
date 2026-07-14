package amiss.api.config;

import amiss.api.persistence.jpa.DegreeJpaRepository;
import amiss.api.persistence.jpa.JobCatalogJpaRepository;
import amiss.api.persistence.jpa.JpaDegreeCatalog;
import amiss.api.persistence.jpa.JpaJobCatalog;
import amiss.api.persistence.jpa.JpaSaveDegrees;
import amiss.api.persistence.jpa.JpaSaveRepository;
import amiss.api.persistence.jpa.JpaTurndowns;
import amiss.api.persistence.jpa.JpaUserRepository;
import amiss.api.persistence.jpa.SaveDegreeJpaRepository;
import amiss.api.persistence.jpa.SaveJpaRepository;
import amiss.api.persistence.jpa.SaveTurndownJpaRepository;
import amiss.api.persistence.jpa.UserJpaRepository;
import amiss.application.config.ActionCosts;
import amiss.application.port.DegreeCatalog;
import amiss.application.port.JobCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.application.port.Turndowns;
import amiss.application.port.UserRepository;
import amiss.application.service.save.SaveGameServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API's composition root. Each repository port is backed by a thin adapter over the
 * generated Spring Data interfaces (KAN-34/KAN-54) — {@code UserJpaRepository}, {@code
 * SaveJpaRepository}, {@code JobCatalogJpaRepository}, {@code DegreeJpaRepository}, {@code
 * SaveDegreeJpaRepository}, {@code SaveTurndownJpaRepository} — querying entities validated
 * against the Flyway schema (KAN-33). Services stay port-typed, so a persistence swap is
 * invisible above this class — proven when the Swing/JDBC side (KAN-51) and the legacy
 * user-stats/jobs ports (KAN-54) were retired without touching a rule.
 */
@Configuration
public class PersistenceConfig {

    @Bean
    UserRepository userRepository(UserJpaRepository users) {
        return new JpaUserRepository(users);
    }

    @Bean
    SaveRepository saveRepository(SaveJpaRepository saves) {
        return new JpaSaveRepository(saves);
    }

    @Bean
    JobCatalog jobCatalog(JobCatalogJpaRepository jobs) {
        return new JpaJobCatalog(jobs);
    }

    @Bean
    DegreeCatalog degreeCatalog(DegreeJpaRepository degrees) {
        return new JpaDegreeCatalog(degrees);
    }

    @Bean
    SaveDegrees saveDegrees(SaveDegreeJpaRepository saveDegrees) {
        return new JpaSaveDegrees(saveDegrees);
    }

    @Bean
    Turndowns turndowns(SaveTurndownJpaRepository turndowns) {
        return new JpaTurndowns(turndowns);
    }

    /**
     * The save-scoped rules layer's composition root (KAN-53/KAN-54), production-wired
     * with a real 1–100 roll; tests inject pinned rolls instead.
     */
    @Bean
    SaveGameServices saveGameServices(SaveRepository saves, JobCatalog jobs, DegreeCatalog degrees,
            SaveDegrees saveDegrees, Turndowns turndowns, ActionCosts costs) {
        return SaveGameServices.withRandomRolls(saves, jobs, degrees, saveDegrees, turndowns, costs);
    }
}
