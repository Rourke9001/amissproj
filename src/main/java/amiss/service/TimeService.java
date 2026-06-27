package amiss.service;

import amiss.repository.UserRepository;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Time, board position and round bookkeeping for the current player (was
 * {@code CalcDuration}). Swing-free and constructor-injected with its repository and the
 * current username; persistence failures are logged, never surfaced to the UI.
 */
public class TimeService {

    private static final Logger log = LoggerFactory.getLogger(TimeService.class);

    private final UserRepository users;
    private final String username;

    public TimeService(UserRepository users, String username) {
        this.users = users;
        this.username = username;
    }

    /**
     * Returns the x-coord
     * @return Returns the x-coord
     */
    public int getX() {
        try {
            return users.getXpos(username);
        } catch (SQLException ex) {
            log.warn("Failed to get x-pos", ex);
        }
        return 0;
    }

    /**
     * return the y-coord
     * @return Returns the y-coord
     */
    public int getY() {
        try {
            return users.getYpos(username);
        } catch (SQLException ex) {
            log.warn("Failed to get y-pos", ex);
        }
        return 0;
    }

    /**
     * Calculates the distance between 2 locations
     * @param xpos users x pos
     * @param ypos users y pos
     * @return returns the distance between two locations
     */
    public int getMulti(int xpos, int ypos) {
        int row = xpos;
        int col = ypos;
        int oldRow = getX();
        int oldCol = getY();

        int multi = Math.abs(oldRow - row) + Math.abs(oldCol - col);

        if ((Math.abs(row - oldRow) == 1 && Math.abs(col - oldCol) == 3) && (row != 3 && row != 0 && oldRow != 3 && oldRow != 0) || (Math.abs(col - oldCol) == 1 && Math.abs(row - oldRow) == 3) && (col != 3 && col != 0 && oldCol != 3 && oldCol != 0)) {
            multi = multi + 2;
        } else if ((Math.abs(row - oldRow) == 3 && col == oldCol && (col != 0 && col != 3)) || (Math.abs(col - oldCol) == 3 && row == oldRow && (row != 0 && row != 3))) {
            multi = multi + 2;
        } else if (Math.abs(oldCol - col) == 3 && row != oldRow && !((row == 0 || row == 3) || (oldRow == 0 || oldRow == 3))) {
            multi = multi + 2;
        }

        return multi;
    }

    /**
     * Updates the database to set the users position
     * @param xpos users x pos
     * @param ypos users y pos
     */
    public void setPos(int xpos, int ypos) {
        try {
            users.updatePosition(username, xpos, ypos);
        } catch (SQLException ex) {
            log.warn("Failed to update position", ex);
        }
    }

    /**
     * Updates the database the the users remaining time
     * @param tm value used to set time
     */
    public void setTime(int tm) {
        try {
            users.updateTime(username, tm);
        } catch (SQLException ex) {
            log.warn("Failed to update time", ex);
        }
    }

    /**
     * Gets the users remaining time from the database
     * @param mult the amount of time taken to complete an action
     * @return Return the users current time
     */
    public String getNewTime(int mult) {
        int multi = mult;
        int time;
        int hours = -1;
        int mins = -1;
        String min = "0";

        try {
            time = users.getTime(username);
            if (time != -1) {

                for (int i = 0; i < multi; i++) {
                    time = time - 10;
                }

                if (time < 0) {
                    return "Not Enough Time";
                } else {
                    hours = time / 60;
                    mins = time % 60;
                    setTime(time);

                    if (mins == 0) {
                        min = "00";
                    } else {
                        min = Integer.toString(mins);
                    }
                    return (hours + ":" + min);

                }
            }
        } catch (SQLException ex) {
            log.warn("Failed to get time", ex);
        }

        return "failed to get time";
    }

    /**
     * Gets the current round the user is on
     * @return Returns the round the user is on
     */
    public String getRound() {
        try {
            int round = users.getRound(username);
            if (round != -1) {
                return round + "";
            }
        } catch (SQLException ex) {
            log.warn("Failed to get round", ex);
        }
        return "failed to get round";
    }

    /**
     * Updates the data base to set the current round of the user
     */
    public void setRound() {
        int round = Integer.parseInt(getRound()) + 1;
        try {
            users.updateRound(username, round);
        } catch (SQLException ex) {
            log.warn("Failed to update round", ex);
        }
    }

    @Override
    public String toString() {
        return getX() + ":" + getY();
    }
}
