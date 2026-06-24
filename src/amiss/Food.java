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
 * Class that relates to the user food items and stored food
 * @author The Rourke
 */
public class Food {

    /**
     *Updates the users food items and stored food
     */
    public Food() {
    }

    /**
     * Updates the database 
     * @param count the amount of food you buying
     * @param txaNotification text area field to display errors
     */
    public void setFood(int count,JTextArea txaNotification) {

        String userName = user.getUser();
        int eat = getFood(txaNotification);
        int addFood = count;

        if ((eat == 0 || eat == 1) && count == 1) {
            eat = 1;
        } else {
            addFood = count;
            eat = eat + addFood;
        }

        try {
            db.update("UPDATE tbluser SET eat = ? WHERE name = ?", eat, userName);
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nfailed to update food");
        }

    }

    /**
     * returns how much stored food the user has
     * @param txaNotification text area field to display errors
     * @return returns how much stored food the user has
     */
    public int getFood(JTextArea txaNotification) {

        String userName = user.getUser();

        try {
            return db.queryForInt("SELECT eat FROM tbluser WHERE name = ?", 0, userName);
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nfailed to get has eaten");
        }
        return 0;

    }

    /**
     * returns if the user ate the previous round
     * @param txaNotification text area field to display errors
     * @return returns if the user ate the previous round
     */
    public boolean getEat(JTextArea txaNotification) {

        String userName = user.getUser();

        boolean eaten = false;

        try {
            int num = db.queryForInt("SELECT eat FROM tbluser WHERE name = ?", -1, userName);
            if (num != -1) {
                if (num == 0) {
                    eaten = false;
                    return eaten;
                } else {
                    eaten = true;
                    setFood(-1,txaNotification);
                    return eaten;
                }
            }
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nfailed to get has eaten");
        }

        return false;

    }
}
