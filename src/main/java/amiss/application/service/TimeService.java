package amiss.application.service;

import amiss.application.port.UserRepository;
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
     * Spends {@code cost} hours of the player's remaining weekly time and returns the new
     * remaining time formatted for the timer label, e.g. {@code "54h"}. Pass {@code 0} to read
     * the current time without spending any. Returns {@code "Not Enough Time"} (and spends
     * nothing) if the action would overrun the week; {@code "0h"} means the week is used up.
     *
     * @param cost the whole hours the action takes (0 to just read the clock)
     * @return the remaining time as {@code "Nh"}, or {@code "Not Enough Time"}
     */
    public String getNewTime(int cost) {
        try {
            int time = users.getTime(username);
            if (time != -1) {
                time = time - cost;
                if (time < 0) {
                    return "Not Enough Time";
                }
                setTime(time);
                return time + "h";
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
