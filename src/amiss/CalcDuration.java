/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amiss;

import static amiss.MainGameGUI.db;
import static amiss.MainGameGUI.user;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that updates the time whenever a user does an action
 * @author The Rourke
 */
public class CalcDuration {

    private static final Logger log = LoggerFactory.getLogger(CalcDuration.class);

    /**
     * Updates the time whenever a user does an action
     */
    public CalcDuration() {
    }

    

    
    /**
     * Returns the x-coord
     * @return Returns the x-coord
     */
        public int getX() {
        int oldRow;
        String userName = user.getUser();

        try {
            ResultSet old = db.query("SELECT xpos FROM tbluser WHERE name = '" + userName + "'");
            if (old.next()) {
                oldRow = old.getInt("xpos");
                return oldRow;
            }

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
        int oldCol;
        String userName = user.getUser();

        try {
            ResultSet old = db.query("SELECT ypos FROM tbluser WHERE name = '" + userName + "'");
            if (old.next()) {
                oldCol = old.getInt("ypos");
                return oldCol;
            }

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
        int row = xpos;
        int col = ypos;
        String userName = user.getUser();

        try {
            db.update("UPDATE tbluser SET xpos = " + row + ",ypos = " + col + " WHERE name = '" + userName + "'");
        } catch (SQLException ex) {
            log.warn("Failed to update position", ex);
        }

    }

    /**
     * Updates the database the the users remaining time
     * @param tm value used to set time
     */
    public void setTime(int tm) {
        int time = tm;
        String userName = user.getUser();
        try {
            db.update("update tbluser set time = " + time + " WHERE name = '" + userName + "'");

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
        String userName = user.getUser();
        int time;
        int hours = -1;
        int mins = -1;
        String min = "0";

        try {
            ResultSet rs = db.query("SELECT time FROM tbluser WHERE name = '" + userName + "'");
            if (rs.next()) {
                time = rs.getInt("time");

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
        String userName = user.getUser();
        int round;

        try {
            ResultSet rs = db.query("SELECT round FROM tbluser WHERE name = '" + userName + "'");
            if (rs.next()) {
                round = rs.getInt("round");
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
        String userName = user.getUser();
        int round = Integer.parseInt(getRound()) + 1;
        try {
            db.update("update tbluser set round = " + round + " WHERE name = '" + userName + "'");
        } catch (SQLException ex) {
            log.warn("Failed to update round", ex);
        }
    }

    @Override
    public String toString() {
        return getX() + ":" + getY();
    }

}
