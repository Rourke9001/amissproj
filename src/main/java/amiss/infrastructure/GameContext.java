package amiss.infrastructure;

import amiss.application.port.HelpRepository;
import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import amiss.application.service.GameServices;
import amiss.domain.model.User;
import amiss.infrastructure.persistence.jdbc.Jdbc;
import amiss.infrastructure.persistence.jdbc.JdbcHelpRepository;
import amiss.infrastructure.persistence.jdbc.JdbcJobRepository;
import amiss.infrastructure.persistence.jdbc.JdbcUserRepository;
import amiss.infrastructure.persistence.jdbc.JdbcUserStatsRepository;

/**
 * Composition root. Opens the JDBC connection and binds the concrete {@code Jdbc*Repository}
 * adapters to the application's repository ports. The presentation layer builds one of these
 * and asks it for the ports it needs or for a per-player {@link GameServices}; nothing in the
 * UI ever touches JDBC directly.
 *
 * <p>This is the single seam where a persistence technology is chosen. Swapping to Spring
 * Data / JPA (or an in-memory fake for tests) means providing a different context here — the
 * services and Swing screens are untouched, because they depend only on the ports.
 */
public final class GameContext implements AutoCloseable {

    private final Jdbc jdbc;
    private final UserRepository users;
    private final UserStatsRepository userStats;
    private final JobRepository jobs;
    private final HelpRepository help;

    public GameContext() {
        this.jdbc = new Jdbc();
        this.users = new JdbcUserRepository(jdbc);
        this.userStats = new JdbcUserStatsRepository(jdbc);
        this.jobs = new JdbcJobRepository(jdbc);
        this.help = new JdbcHelpRepository(jdbc);
    }

    public UserRepository users() {
        return users;
    }

    public UserStatsRepository userStats() {
        return userStats;
    }

    public JobRepository jobs() {
        return jobs;
    }

    public HelpRepository help() {
        return help;
    }

    /** Builds the wired game-rule services for {@code user}. */
    public GameServices servicesFor(User user) {
        return new GameServices(user, users, userStats, jobs);
    }

    /** Closes the underlying JDBC connection. */
    @Override
    public void close() {
        jdbc.close();
    }
}
