/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amiss;

import static amiss.MainGameGUI.user;
import amiss.repository.UserRepository;
import amiss.repository.UserStatsRepository;
import java.sql.SQLException;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that updates the users cash when buying an item or working
 *
 * @author The Rourke
 */
public class Stats {

    private static final Logger log = LoggerFactory.getLogger(Stats.class);

    static GetAJob job = new GetAJob();
    static CalcDuration dist = new CalcDuration();
    static Food eat = new Food();

    /**
     * Updates the users cash when buying an item or working
     */
    public Stats() {
    }

    private UserRepository users() {
        return new UserRepository(MainGameGUI.db);
    }

    private UserStatsRepository stats() {
        return new UserStatsRepository(MainGameGUI.db);
    }

    /**
     * returns if the user bought the item or not
     *
     * @param prc the price of the item
     * @return returns if the user bought the item or not
     */
    public String buy(String prc) {

        int price = Validation.parseIntOrDefault(prc, 0);
        String userName = user.getUser();

        int currCash = getCash();
        currCash = currCash - price;

        if (currCash < 0) {

            return "not enough cash, you only have R" + (currCash + price);

        } else {
            try {
                users().updateCash(userName, currCash);
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
            return users().getCash(userName);
        } catch (SQLException ex) {
            log.warn("Failed to get cash", ex);
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
                users().updateCash(userName, currCash);
                return "You were deducted R10 for not paying rent \nYou now have R" + currCash;
            } catch (SQLException ex) {
                return ("failed to update cash");
            }
        } else {
            currCash = earn + getCash();

            try {
                users().updateCash(userName, currCash);
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
            users().updateRent(userName, num);
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
            return users().getRent(userName);
        } catch (SQLException ex) {
            return (-1);
        }

    }

    public void setDebt(JTextArea txaNotification, int num) {

        String userName = user.getUser();
        try {
            users().addDebt(userName, num);
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nFailed to set debt");
        }
    }

    public void payDebt() {

        String userName = user.getUser();
        try {
            users().subtractDebt(userName, 10);
        } catch (SQLException ex) {
            log.warn("Failed to pay debt", ex);
        }
    }

    public int getDebt() {

        String userName = user.getUser();
        try {
            return users().getDebt(userName);
        } catch (SQLException ex) {
            return (-1);
        }

    }

    /**
     * updates the work experience for the user
     *
     * @param txaNotification text area field to display errors
     */
    public void updateWork(JTextArea txaNotification) {
        String userName = user.getUser();
        try {
            stats().incrementWork(userName);
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
            return stats().getWork(userName);
        } catch (SQLException ex) {
            return ("Failed to get work");
        }
    }

    /**
     * updates the users happiness
     *
     * @param txaNotification text area field to display errors
     */
    public void updateHappiness(JTextArea txaNotification) {
        String userName = user.getUser();

        try {
            stats().incrementHappiness(userName);
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
            return stats().getHappiness(userName);
        } catch (SQLException ex) {
            return ("Failed to get Happiness");
        }
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
            stats().resetStats(userName);
            users().resetUser(userName);
        } catch (SQLException ex) {
            txaNotification.setText(txaNotification.getText() + "\nFailed to Reset stats");
        }
    }
}
