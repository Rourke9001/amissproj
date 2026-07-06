package amiss.api.config;

import amiss.api.error.PlayerNotFoundException;
import amiss.application.config.ActionCosts;
import amiss.application.port.JobRepository;
import amiss.application.port.PersistenceFailureException;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import amiss.application.service.GameServices;
import amiss.domain.model.User;
import org.springframework.stereotype.Component;

/**
 * Bridges Spring's singleton world to the core's per-player services:
 * {@link GameServices} carries the acting player's username, so one is built
 * per request. Construction is pure wiring (no I/O beyond the player lookup),
 * which keeps this cheap and proxy-free — no request scoping needed.
 */
@Component
public class GameServicesFactory {

    private final UserRepository users;
    private final UserStatsRepository userStats;
    private final JobRepository jobs;
    private final ActionCosts costs;

    public GameServicesFactory(UserRepository users, UserStatsRepository userStats,
            JobRepository jobs, ActionCosts costs) {
        this.users = users;
        this.userStats = userStats;
        this.jobs = jobs;
        this.costs = costs;
    }

    /**
     * Loads the player and assembles their services.
     *
     * @throws PlayerNotFoundException     if no such player exists (→ 404)
     * @throws PersistenceFailureException if the lookup itself fails (→ 500)
     */
    public GameServices forPlayer(String username) {
        User user = users.findByName(username)
                .orElseThrow(() -> new PlayerNotFoundException(username));
        return new GameServices(user, users, userStats, jobs, costs);
    }
}
