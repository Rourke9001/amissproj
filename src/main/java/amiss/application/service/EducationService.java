package amiss.application.service;

import amiss.application.port.UserStatsRepository;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The player's university progress and education level (was {@code University}).
 * Swing-free and constructor-injected with its repository and the current username;
 * persistence failures are logged, not shown in the UI.
 */
public class EducationService {

    private static final Logger log = LoggerFactory.getLogger(EducationService.class);

    private final UserStatsRepository stats;
    private final String username;

    public EducationService(UserStatsRepository stats, String username) {
        this.stats = stats;
        this.username = username;
    }

    /**
     * returns the users education
     * @return returns the users education progress
     */
    public int getEducation() {
        try {
            return stats.getEducation(username);
        } catch (SQLException ex) {
            return (-1);
        }
    }

    /**
     * advances the users education level by one
     */
    public void setEducation() {
        int edu = getEducation() + 1;
        try {
            stats.updateEducation(username, edu);
        } catch (SQLException ex) {
            log.warn("Failed to update education", ex);
        }
    }

    /**
     * returns whether the user has enrolled
     * @return returns whether the user has enrolled
     */
    public boolean hasEnrolled() {
        int currProg = getProg();
        if (currProg == 0) {
            return false;
        } else if (currProg == 1) {
            return true;
        }
        return false;
    }

    /**
     * returns the users education progress
     * @return returns the users education progress
     */
    public int getProg() {
        try {
            return stats.getEduprog(username);
        } catch (SQLException ex) {
            return (-1);
        }
    }

    /**
     * @param prog the users new progress
     */
    public void setProg(int prog) {
        try {
            stats.updateEduprog(username, prog);
        } catch (SQLException ex) {
            log.warn("Failed to set progress", ex);
        }
    }
}
