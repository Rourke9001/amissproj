/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amiss;

import static amiss.MainGameGUI.db;
import static amiss.MainGameGUI.user;
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

    /**
     * returns the users education 
     * @return returns the users education progress
     */
    public int getEducation() {

        String userName = user.getUser();

        try {
            return db.queryForInt("SELECT education from tbluserstats where name = ?", -1, userName);
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
            db.update("UPDATE tbluserstats set education = ? where name = ?", edu, userName);
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
            return db.queryForInt("SELECT eduprog from tbluserstats where name = ?", -1, userName);
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
            db.update("Update tbluserstats set eduprog = ? where name = ?", currProg, userName);
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nfailed to set progress");
        }
    }
}
