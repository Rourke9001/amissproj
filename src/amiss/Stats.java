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
import javax.swing.JLabel;
import javax.swing.JTextArea;

/**
 * Class that updates the users cash when buying an item or working
 *
 * @author The Rourke
 */
public class Stats {

    static GetAJob job = new GetAJob();
    static CalcDuration dist = new CalcDuration();
    static Food eat = new Food();

    /**
     * Updates the users cash when buying an item or working
     */
    public Stats() {
    }

    /**
     * returns if the user bought the item or not
     *
     * @param prc the price of the item
     * @return returns if the user bought the item or not
     */
    public String buy(String prc) {

        int price = Integer.parseInt(prc);
        String userName = user.getUser();

        int currCash = getCash();
        currCash = currCash - price;

        if (currCash < 0) {

            return "not enough cash, you only have R" + (currCash + price);

        } else {
            try {
                db.update("update tbluser SET cash = " + currCash + " WHERE name = '" + userName + "'");
                return "You spent R" + price + ", You have R" + currCash + " left";
            } catch (SQLException ex) {
                return ("failed to purchase");
            }
        }
    }

    /**
     * returns the users cash
     *
     * @return returns the users cash
     */
    public int getCash() {

        String userName = user.getUser();
        try {

            ResultSet cash = db.query("SELECT cash FROM tbluser WHERE name = '" + userName + "'");
            if (cash.next()) {
                int currCash = cash.getInt("cash");
                return currCash;
            }
        } catch (SQLException ex) {
            System.out.println("Failed to Get Cash");;
        }
        return -1;

    }

    /**
     * returns how much money the user has
     *
     * @param earn how much the user earns while working
     * @return returns how much money the user has
     */
    public String setCash(int earn) {
        String userName = user.getUser();
        int currCash = 0;
        if (getDebt() > 0) {
            currCash = earn + getCash() - 10;
            payDebt();
            try {
                db.update("update tbluser SET cash = " + currCash + " WHERE name = '" + userName + "'");
                return "You were deducted R10 for not paying rent \nYou now have R" + currCash;
            } catch (SQLException ex) {
                return ("failed to update cash");
            }
        } else {
            currCash = earn + getCash();

            try {
                db.update("update tbluser SET cash = " + currCash + " WHERE name = '" + userName + "'");
                return "You now have R" + currCash;
            } catch (SQLException ex) {
                return ("failed to update cash");
            }
        }
    }

    /**
     * Sets the user rent status
     *
     * @param txaNotification text area field to display errors
     */
    public void setRent(JTextArea txaNotification, int num) {

        String userName = user.getUser();
        try {
            db.update("update tbluser SET rent = " + num + " WHERE name = '" + userName + "'");
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nfailed to set rent");
        }

    }

    /**
     * returns the the users rent status
     *
     * @return returns the the users rent status
     */
    public int getRent() {

        String userName = user.getUser();
        try {

            ResultSet paid = db.query("SELECT rent FROM tbluser WHERE name = '" + userName + "'");
            if (paid.next()) {
                int rent = paid.getInt("rent");
                return rent;
            }
        } catch (SQLException ex) {
            return (-1);
        }
        return -1;

    }

    public void setDebt(JTextArea txaNotification, int num) {

        String userName = user.getUser();
        try {
            db.update("UPDATE tbluser SET debt = debt + '" + num + "' WHERE name = '" + userName + "'");
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nFailed to set debt");
        }
    }

    public void payDebt() {

        String userName = user.getUser();
        try {
            db.update("UPDATE tbluser SET debt = debt - '" + 10 + "' WHERE name = '" + userName + "'");
        } catch (SQLException ex) {
            
        }
    }

    public int getDebt() {

        String userName = user.getUser();
        try {

            ResultSet debt = db.query("SELECT debt FROM tbluser WHERE name = '" + userName + "'");
            if (debt.next()) {
                int debtAmount = debt.getInt("debt");
                return debtAmount;
            }
        } catch (SQLException ex) {
            return (-1);
        }
        return -1;

    }

    /**
     * updates the work experience for the user
     *
     * @param txaNotification text area field to display errors
     */
    public void updateWork(JTextArea txaNotification) {
        String userName = user.getUser();
        try {
            db.update("UPDATE tbluserstats SET work = work + '" + 1 + "' WHERE name = '" + userName + "'");
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nFailed to update work stats");
        }
    }

    /**
     * returns the users work experience
     *
     * @return returns the users work experience
     */
    public String getWork() {
        try {
            String userName = user.getUser();

            ResultSet rs = db.query("SELECT work FROM tbluserstats WHERE name = '" + userName + "'");
            if (rs.next()) {
                String work = rs.getString("work");
                return work;
            }
        } catch (SQLException ex) {
            return ("Failed to get work");
        }
        return null;
    }

    /**
     * updates the users happiness
     *
     * @param txaNotification text area field to display errors
     */
    public void updateHappiness(JTextArea txaNotification) {
        String userName = user.getUser();

        try {
            db.update("UPDATE tbluserstats SET happiness = happiness + '" + 1 + "' WHERE name = '" + userName + "'");
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nFailed to updated happiness stats");
        }
    }

    /**
     * returns the users happiness
     *
     * @return returns the users happiness
     */
    public String getHappiness() {
        try {
            String userName = user.getUser();

            ResultSet rs = db.query("SELECT happiness FROM tbluserstats WHERE name = '" + userName + "'");
            if (rs.next()) {
                String happy = rs.getString("happiness");
                return happy;
            }
        } catch (SQLException ex) {
            return ("Failed to get Happiness");
        }
        return null;
    }

    /**
     * Checks if the user is able to work and updates the users cash
     *
     * @param txaNotification text area field to display errors
     * @param lblTimer label to display remaining time
     * @param lblMoney label to display users cash
     */
    public void workMain(JTextArea txaNotification, JLabel lblTimer, JLabel lblMoney) {
        String clothes = job.getJobClothes();

        if (clothes == null) {

            String time = dist.getNewTime(6);
            if (time.equals("Not Enough Time")) {
                txaNotification.setText(txaNotification.getText() + "\n" + time);
            } else {
                updateWork(txaNotification);
                txaNotification.setText(txaNotification.getText() + "\n" + job.toString() + "\n" + setCash(job.getEarnings()));
                lblTimer.setText(time);
                lblMoney.setText(Integer.toString(getCash()));
            }
        } else {
            txaNotification.setText(txaNotification.getText() + "\n\n" + clothes);
        }
    }

    /**
     * Checks if the user has enough time to eat then updates the users stored
     * food
     *
     * @param txaNotification text area field to display errors
     * @param lblTimer label to display remaining time
     * @param lblMoney label to display users cash
     * @param price price of the item being purchased
     */
    public void eatMain(JTextArea txaNotification, JLabel lblTimer, JLabel lblMoney, int price) {
        int food = eat.getFood(txaNotification);
        String time = dist.getNewTime(1);
        if (time.equals("Not Enough Time")) {
            txaNotification.setText(txaNotification.getText() + "\n" + time);
        } else if (getCash() < price) {
            txaNotification.setText(txaNotification.getText() + "\nNot Enough Cash, You only have R" + getCash());
        } else {
            lblTimer.setText(time);
            if (food >= 1) {
                eat.setFood(0, txaNotification);
            } else {
                eat.setFood(1, txaNotification);
            }
            txaNotification.setText(txaNotification.getText() + "\n" + buy(Integer.toString(price)) + "\nThat Was Yummy, One point into happiness");
            updateHappiness(txaNotification);
            lblMoney.setText(Integer.toString(getCash()));
        }

    }

    /**
     * resets the users stats to start the game over
     *
     * @param txaNotification text area field to display errors
     */
    public void reset(JTextArea txaNotification) {
        String userName = user.getUser();

        try {
            db.update("UPDATE amissdb.tbluserstats SET `happiness` = 0, `education` = 0, `work` = 0 WHERE name = '" + userName + "'");
            db.update("UPDATE amissdb.tbluser SET `xpos` = 0, `ypos` = 0, `time` = 720, `cash` = 100, `round` = 1, `job` = 'Unemployed', `clothing` = 1,`eat` = 0, `rent` = 1, `debt` = 0 WHERE name = '" + userName + "'");
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nFailed to Reset stats");
        }
    }
}
