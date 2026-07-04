package amiss.application.service;

import amiss.application.config.ActionCosts;
import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import amiss.domain.model.User;

/**
 * Wires up the game's service layer for one signed-in {@link User} over a set of
 * repository <em>ports</em>. Given the ports (whose concrete JDBC adapters are built by the
 * infrastructure composition root, {@code amiss.infrastructure.GameContext}), it constructs
 * every service once in dependency order. A screen holds a single {@code GameServices} and
 * reaches the rules through {@link #time()}, {@link #stats()}, {@link #jobs()},
 * {@link #food()} and {@link #education()}. This class depends only on ports and the domain,
 * never on a database — so the same wiring survives a swap of the persistence technology.
 */
public class GameServices {

    private final TimeService time;
    private final EducationService education;
    private final JobService jobs;
    private final FoodService food;
    private final StatsService stats;
    private final ActionCosts costs;

    public GameServices(User user, UserRepository users, UserStatsRepository userStats,
            JobRepository jobRepo) {
        this(user, users, userStats, jobRepo, ActionCosts.defaults());
    }

    public GameServices(User user, UserRepository users, UserStatsRepository userStats,
            JobRepository jobRepo, ActionCosts costs) {
        String username = user.getUser();

        this.costs = costs;
        this.time = new TimeService(users, username);
        this.education = new EducationService(userStats, username);
        this.jobs = new JobService(jobRepo, users, education, username);
        this.food = new FoodService(users, username);
        this.stats = new StatsService(users, userStats, jobs, time, food, costs, username);
    }

    public TimeService time() {
        return time;
    }

    public EducationService education() {
        return education;
    }

    public JobService jobs() {
        return jobs;
    }

    public FoodService food() {
        return food;
    }

    public StatsService stats() {
        return stats;
    }

    /** The action cost table this game was wired with (minutes per action). */
    public ActionCosts costs() {
        return costs;
    }
}
