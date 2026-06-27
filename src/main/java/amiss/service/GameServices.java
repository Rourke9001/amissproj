package amiss.service;

import amiss.DB;
import amiss.User;
import amiss.repository.JobRepository;
import amiss.repository.UserRepository;
import amiss.repository.UserStatsRepository;

/**
 * Composition root for the game's service layer: given the signed-in {@link User} and a
 * {@link DB}, it builds the repositories once and wires up every service (replacing the
 * old {@code MainGameGUI.db}/{@code MainGameGUI.user} ambient statics). A screen holds a
 * single {@code GameServices} and reaches the rules through {@link #time()},
 * {@link #stats()}, {@link #jobs()}, {@link #food()} and {@link #education()}.
 */
public class GameServices {

    private final TimeService time;
    private final EducationService education;
    private final JobService jobs;
    private final FoodService food;
    private final StatsService stats;

    public GameServices(User user, DB db) {
        String username = user.getUser();
        UserRepository users = new UserRepository(db);
        UserStatsRepository userStats = new UserStatsRepository(db);
        JobRepository jobRepo = new JobRepository(db);

        this.time = new TimeService(users, username);
        this.education = new EducationService(userStats, username);
        this.jobs = new JobService(jobRepo, users, education, username);
        this.food = new FoodService(users, username);
        this.stats = new StatsService(users, userStats, jobs, time, food, username);
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
}
