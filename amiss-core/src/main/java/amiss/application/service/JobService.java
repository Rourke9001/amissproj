package amiss.application.service;

import amiss.application.config.ActionCosts;
import amiss.application.port.JobRepository;
import amiss.application.port.PersistenceFailureException;
import amiss.application.port.UserRepository;
import amiss.domain.validation.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applying for and describing the player's job (was {@code GetAJob}). Swing-free and
 * constructor-injected; depends on {@link EducationService} to check a job's entry
 * requirements. Persistence failures are logged, not shown in the UI.
 */
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobs;
    private final UserRepository users;
    private final EducationService education;
    private final TimeService time;
    private final ActionCosts costs;
    private final String username;

    public JobService(JobRepository jobs, UserRepository users, EducationService education,
            TimeService time, ActionCosts costs, String username) {
        this.jobs = jobs;
        this.users = users;
        this.education = education;
        this.time = time;
        this.costs = costs;
        this.username = username;
    }

    /**
     * Returns if the User Got the job. Checks if the user is eligible for a job and
     * updates the users job in the database.
     * @param job field name of the job
     * @return Returns if the User Got the job
     */
    public String applyForJob(String job) {
        String selected = job;
        int neededEdu = neededEdu(selected);
        int actualEdu = education.getEducation();
        if (actualEdu < neededEdu) {
            return "not enough education";
        } else {
            setJob(selected);
            return "Well Done! You Got The Job, You will earn R" + getEarnings() + " for every hour you Work!";
        }
    }

    private int neededEdu(String jb) {
        try {
            return jobs.getRequiredEducation(jb);
        } catch (PersistenceFailureException ex) {
            return (-1);
        }
    }

    /**
     * Applies for {@code jobName}: checks it exists, charges the application time, then
     * checks the player's education. Mirrors {@code EmploymentGUI.btnCookActionPerformed}
     * exactly: the 4h application time is charged even when education is insufficient (the
     * game rule — applying always takes 4 hours, win or lose). Unlike {@link #neededEdu},
     * this calls the port directly so a SQL failure ({@link ApplyOutcome.Status#FAILED}) can
     * be told apart from a genuinely unknown job ({@link ApplyOutcome.Status#UNKNOWN_JOB}),
     * which Swing's fixed job buttons can never send but the API might.
     * @param jobName field name of the job
     * @return the typed apply outcome
     */
    public ApplyOutcome apply(String jobName) {
        int neededEdu;
        try {
            neededEdu = jobs.getRequiredEducation(jobName);
        } catch (PersistenceFailureException ex) {
            log.warn("Failed to check required education", ex);
            return new ApplyOutcome(ApplyOutcome.Status.FAILED, -1, jobName, -1);
        }
        if (neededEdu == -1) {
            return new ApplyOutcome(ApplyOutcome.Status.UNKNOWN_JOB, -1, jobName, -1);
        }

        TimeSpend clock = time.spendMinutes(0);
        if (clock.weekOver()) {
            return new ApplyOutcome(ApplyOutcome.Status.WEEK_OVER, clock.remainingMinutes(), jobName, -1);
        }

        TimeSpend spend = time.spendMinutes(costs.applyJobMinutes());
        if (spend.rejected()) {
            return new ApplyOutcome(ApplyOutcome.Status.INSUFFICIENT_TIME, spend.remainingMinutes(), jobName, -1);
        }

        int actualEdu = education.getEducation();
        if (actualEdu < neededEdu) {
            return new ApplyOutcome(ApplyOutcome.Status.INSUFFICIENT_EDUCATION, spend.remainingMinutes(), jobName, -1);
        }

        setJob(jobName);
        return new ApplyOutcome(ApplyOutcome.Status.HIRED, spend.remainingMinutes(), jobName, getEarnings());
    }

    private void setJob(String jb) {
        try {
            users.updateJob(username, jb);
        } catch (PersistenceFailureException ex) {
            log.warn("Failed to update job", ex);
        }
    }

    /**
     * returns how much money the user earned
     * @return returns how much money the user earned
     */
    public int getEarnings() {
        String job = getJob();
        try {
            return jobs.getSalary(job);
        } catch (PersistenceFailureException ex) {
            return -1;
        }
    }

    /**
     * returns the name of the job the user currently holds (or {@code "Unemployed"})
     * @return returns the current job name
     */
    public String getJob() {
        try {
            return users.getJob(username);
        } catch (PersistenceFailureException ex) {
            return ("failed to get earnings");
        }
    }

    /**
     * returns the location of the users job
     * @return returns the location of the users job
     */
    public String getLocation() {
        String job = getJob();
        try {
            return jobs.getLocation(job);
        } catch (PersistenceFailureException ex) {
            return ("failed to get location");
        }
    }

    /**
     * Sets the users clothes
     * @param clothes clothes item purchased
     */
    public void setClothes(int clothes) {
        try {
            users.updateClothing(username, clothes);
        } catch (PersistenceFailureException ex) {
            log.warn("Failed to update clothes", ex);
        }
    }

    /**
     * Returns the clothes the user need for a job
     * @return Returns the clothes the user need for a job
     */
    public String getJobClothes() {
        String job = getJob();
        try {
            String clothes = jobs.getRequiredClothing(job);
            if (clothes != null) {
                int userC = Integer.parseInt(getUserClothes());
                int reqC = Integer.parseInt(clothes);
                if (!(userC >= reqC)) {
                    return "You are not properly dressed for work";
                } else {
                    return null;
                }
            }
        } catch (PersistenceFailureException ex) {
            return ("failed to get clothing");
        }

        return null;
    }

    private String getUserClothes() {
        try {
            return users.getUserClothing(username);
        } catch (PersistenceFailureException ex) {
            return ("Failed to Get User Clothes");
        }
    }

    /**
     * returns the user's clothing level (1 = casual, default when unparseable)
     * @return returns the user's clothing level
     */
    public int getClothingLevel() {
        return Validation.parseIntOrDefault(getUserClothes(), 1);
    }

    @Override
    public String toString() {
        return "You work as a " + getJob() + " and Earn R" + getEarnings();
    }
}
