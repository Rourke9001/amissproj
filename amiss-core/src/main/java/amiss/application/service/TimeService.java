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
     * Spends {@code cost} minutes of the player's remaining weekly time. Pass {@code 0} to
     * read the clock without spending any. A spend that would push the clock below zero is
     * rejected and persists nothing; a persistence failure is logged and reported as a
     * rejection, so no action proceeds on a broken clock.
     *
     * @param cost the whole minutes the action takes (0 to just read the clock)
     * @return the new remaining minutes plus the rejected / week-over flags
     */
    public TimeSpend spendMinutes(int cost) {
        try {
            int time = users.getTime(username);
            if (time != -1) {
                int remaining = time - cost;
                if (remaining < 0) {
                    return new TimeSpend(time, true, time == 0);
                }
                setTime(remaining);
                return new TimeSpend(remaining, false, remaining == 0);
            }
        } catch (SQLException ex) {
            log.warn("Failed to get time", ex);
        }

        return new TimeSpend(0, true, false);
    }

    /** Reads the clock without spending and formats it for a timer label, e.g. {@code "66h"}. */
    public String readClock() {
        return format(spendMinutes(0).remainingMinutes());
    }

    /**
     * Formats a minute count for display: {@code "38h 30m"}, or just {@code "72h"} when the
     * minutes part is zero, or just {@code "45m"} under an hour ({@code 0} renders {@code "0h"}).
     */
    public static String format(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (m == 0) {
            return h + "h";
        }
        if (h == 0) {
            return m + "m";
        }
        return h + "h " + m + "m";
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
