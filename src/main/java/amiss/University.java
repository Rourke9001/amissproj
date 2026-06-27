/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amiss;

import static amiss.MainGameGUI.user;
import amiss.repository.UserStatsRepository;
import java.sql.SQLException;
import javax.swing.JTextArea;

/**
 * Class that updates to users university information
 * @author The Rourke
 */
public class University {

    /**
     * Updates to users university information
     */
    public University() {
    }

    private UserStatsRepository stats() {
        return new UserStatsRepository(MainGameGUI.db);
    }

    /**
     * returns the users education 
     * @return returns the users education progress
     */
    public int getEducation() {

        String userName = user.getUser();

        try {
            return stats().getEducation(userName);
        } catch (SQLException ex) {
            return(-1);
        }

    }

    /**
     * sets the users new education
     * @param txaNotification text area field to display errors
     */
    public void setEducation(JTextArea txaNotification) {
        int edu = getEducation() + 1;
        String userName = user.getUser();

        try {
            stats().updateEducation(userName, edu);
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nfailed to update education");
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

        String userName = user.getUser();
        try {
            return stats().getEduprog(userName);
        } catch (SQLException ex) {
            return(-1);
        }
    }

    /**
     *
     * @param prog the users new progress
     * @param txaNotification text area field to display errors
     */
    public void setProg(int prog,JTextArea txaNotification) {

        int currProg = prog;
        String userName = user.getUser();

        try {
            stats().updateEduprog(userName, currProg);
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nfailed to set progress");
        }
    }
}
