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
 * Class that checks if the user can get a job
 * @author The Rourke
 */
public class GetAJob {

    static University uni = new University();

    /**
     *Checks if the user can get a job
     */
    public GetAJob() {
    }

    /**
     * Returns if the User Got the job
     * Checks if the user is able to able for a job and updates the users job in the database
     * @param job field name of the job
     * @param txaNotification text area field to display errors
     * @return Returns if the User Got the job
     */
    public String applyForJob(String job, JTextArea txaNotification) {
        String selected = job;
        int neededEdu = neededEdu(selected);
        int actualEdu = uni.getEducation();
        if (actualEdu < neededEdu) {
            return "not enough education";
        } else {
            setJob(selected,txaNotification);
            return "Well Done! You Got The Job, You will earn R" + getEarnings() + " for every hour you Work!";
        }

    }

    private int neededEdu(String jb) {
        String job = jb;
        try {
            return db.queryForInt("SELECT education from tbljobs where job = ?", -1, job);
        } catch (SQLException ex) {
            return(-1);
        }
    }

    private void setJob(String jb,JTextArea txaNotification) {
        String job = jb;
        String userName = user.getUser();

        try {
            db.update("UPDATE tbluser set job = ? WHERE name = ?", job, userName);
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\ncouldnt update job");
        }
    }

    /**
     * returns how much money the user earned
     * @return returns how much money the user earned
     */
    public int getEarnings() {
        String job = getJob();

        try {
            return db.queryForInt("SELECT salary from tbljobs where job = ?", -1, job);
        } catch (SQLException ex) {
            return -1;
        }
    }

    private String getJob() {
        String userName = user.getUser();

        try {
            return db.queryForString("SELECT job from tbluser where name = ?", null, userName);
        } catch (SQLException ex) {
            return("failed to get earnings");
        }
    }

    /**
     * returns the location of the users job
     * @return returns the location of the users job
     */
    public String getLocation() {
        String job = getJob();
        try {
            return db.queryForString("SELECT location from tbljobs where job = ?", null, job);
        } catch (SQLException ex) {
            return("failed to get location");
        }
    }

    /**
     * Sets the users clothes
     * @param clothes clothes item purchased
     * @param txaNotification text area field to display errors
     */
    public void setClothes(int clothes,JTextArea txaNotification) {
        String userName = user.getUser();
        try {
            db.update("UPDATE tbluser SET clothing = ? WHERE name = ?", clothes, userName);
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nFailed to update clothes");
        }
    }

    /**
     * Returns the clothes the user need for a job
     * @return Returns the clothes the user need for a job
     */
    public String getJobClothes() {
        String job = getJob();
        try {
            String clothes = db.queryForString("SELECT clothing from tbljobs where job = ?", null, job);
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
            return("failed to get clothing");
        }

        return null;

    }

    private String getUserClothes() {
        String userName = user.getUser();
        try {
            return db.queryForString("SELECT clothing FROM tbluser WHERE name = ?", null, userName);
        } catch (SQLException ex) {
            return("Failed to Get User Clothes");
        }
    }

    @Override
    public String toString() {
        return "You work as a " + getJob() + " and Earn R" + getEarnings();
    }

}
