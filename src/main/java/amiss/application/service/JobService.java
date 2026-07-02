package amiss.application.service;

import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import java.sql.SQLException;
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
    private final String username;

    public JobService(JobRepository jobs, UserRepository users, EducationService education, String username) {
        this.jobs = jobs;
        this.users = users;
        this.education = education;
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
        } catch (SQLException ex) {
            return (-1);
        }
    }

    private void setJob(String jb) {
        try {
            users.updateJob(username, jb);
        } catch (SQLException ex) {
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
        } catch (SQLException ex) {
            return -1;
        }
    }

    private String getJob() {
        try {
            return users.getJob(username);
        } catch (SQLException ex) {
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
        } catch (SQLException ex) {
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
        } catch (SQLException ex) {
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
        } catch (SQLException ex) {
            return ("failed to get clothing");
        }

        return null;
    }

    private String getUserClothes() {
        try {
            return users.getUserClothing(username);
        } catch (SQLException ex) {
            return ("Failed to Get User Clothes");
        }
    }

    @Override
    public String toString() {
        return "You work as a " + getJob() + " and Earn R" + getEarnings();
    }
}
