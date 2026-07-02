package amiss.application.service;
import amiss.domain.model.ActionResult;

import amiss.domain.validation.Validation;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The player's money, rent/debt, work experience and happiness, plus the work/eat action
 * orchestration (was {@code Stats}). Swing-free: {@link #workMain()} and
 * {@link #eatMain(int)} return an {@link ActionResult} of plain values for the screen to
 * render, and persistence failures are logged rather than shown in the UI.
 */
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);

    private final UserRepository users;
    private final UserStatsRepository stats;
    private final JobService job;
    private final TimeService dist;
    private final FoodService eat;
    private final String username;

    public StatsService(UserRepository users, UserStatsRepository stats,
            JobService job, TimeService dist, FoodService eat, String username) {
        this.users = users;
        this.stats = stats;
        this.job = job;
        this.dist = dist;
        this.eat = eat;
        this.username = username;
    }

    /**
     * returns if the user bought the item or not
     * @param prc the price of the item
     * @return returns if the user bought the item or not
     */
    public String buy(String prc) {
        int price = Validation.parseIntOrDefault(prc, 0);

        int currCash = getCash();
        currCash = currCash - price;

        if (currCash < 0) {
            return "not enough cash, you only have R" + (currCash + price);
        } else {
            try {
                users.updateCash(username, currCash);
                return "You spent R" + price + ", You have R" + currCash + " left";
            } catch (SQLException ex) {
                return ("failed to purchase");
            }
        }
    }

    /**
     * returns the users cash
     * @return returns the users cash
     */
    public int getCash() {
        try {
            return users.getCash(username);
        } catch (SQLException ex) {
            log.warn("Failed to get cash", ex);
        }
        return -1;
    }

    /**
     * returns how much money the user has
     * @param earn how much the user earns while working
     * @return returns how much money the user has
     */
    public String setCash(int earn) {
        int currCash;
        if (getDebt() > 0) {
            currCash = earn + getCash() - 10;
            payDebt();
            try {
                users.updateCash(username, currCash);
                return "You were deducted R10 for not paying rent \nYou now have R" + currCash;
            } catch (SQLException ex) {
                return ("failed to update cash");
            }
        } else {
            currCash = earn + getCash();
            try {
                users.updateCash(username, currCash);
                return "You now have R" + currCash;
            } catch (SQLException ex) {
                return ("failed to update cash");
            }
        }
    }

    /**
     * Sets the user rent status
     */
    public void setRent(int num) {
        try {
            users.updateRent(username, num);
        } catch (SQLException ex) {
            log.warn("Failed to set rent", ex);
        }
    }

    /**
     * returns the the users rent status
     * @return returns the the users rent status
     */
    public int getRent() {
        try {
            return users.getRent(username);
        } catch (SQLException ex) {
            return (-1);
        }
    }

    public void setDebt(int num) {
        try {
            users.addDebt(username, num);
        } catch (SQLException ex) {
            log.warn("Failed to set debt", ex);
        }
    }

    public void payDebt() {
        try {
            users.subtractDebt(username, 10);
        } catch (SQLException ex) {
            log.warn("Failed to pay debt", ex);
        }
    }

    public int getDebt() {
        try {
            return users.getDebt(username);
        } catch (SQLException ex) {
            return (-1);
        }
    }

    /**
     * updates the work experience for the user
     */
    public void updateWork() {
        try {
            stats.incrementWork(username);
        } catch (SQLException ex) {
            log.warn("Failed to update work stats", ex);
        }
    }

    /**
     * returns the users work experience
     * @return returns the users work experience
     */
    public String getWork() {
        try {
            return stats.getWork(username);
        } catch (SQLException ex) {
            return ("Failed to get work");
        }
    }

    /**
     * updates the users happiness
     */
    public void updateHappiness() {
        try {
            stats.incrementHappiness(username);
        } catch (SQLException ex) {
            log.warn("Failed to update happiness stats", ex);
        }
    }

    /**
     * returns the users happiness
     * @return returns the users happiness
     */
    public String getHappiness() {
        try {
            return stats.getHappiness(username);
        } catch (SQLException ex) {
            return ("Failed to get Happiness");
        }
    }

    /**
     * Checks if the user is able to work and updates the users cash.
     * @return the notification text, timer and money values for the screen to render
     */
    public ActionResult workMain() {
        String clothes = job.getJobClothes();

        if (clothes == null) {
            String time = dist.getNewTime(6);
            if (time.equals("Not Enough Time")) {
                return ActionResult.message("\n" + time);
            } else {
                updateWork();
                String message = "\n" + job.toString() + "\n" + setCash(job.getEarnings());
                return new ActionResult(message, time, Integer.toString(getCash()));
            }
        } else {
            return ActionResult.message("\n\n" + clothes);
        }
    }

    /**
     * Checks if the user has enough time to eat then updates the users stored food.
     * @param price price of the item being purchased
     * @return the notification text, timer and money values for the screen to render
     */
    public ActionResult eatMain(int price) {
        int food = eat.getFood();
        String time = dist.getNewTime(1);
        if (time.equals("Not Enough Time")) {
            return ActionResult.message("\n" + time);
        } else if (getCash() < price) {
            return ActionResult.message("\nNot Enough Cash, You only have R" + getCash());
        } else {
            if (food >= 1) {
                eat.setFood(0);
            } else {
                eat.setFood(1);
            }
            String message = "\n" + buy(Integer.toString(price)) + "\nThat Was Yummy, One point into happiness";
            updateHappiness();
            return new ActionResult(message, time, Integer.toString(getCash()));
        }
    }

    /**
     * resets the users stats to start the game over
     */
    public void reset() {
        try {
            stats.resetStats(username);
            users.resetUser(username);
        } catch (SQLException ex) {
            log.warn("Failed to reset stats", ex);
        }
    }
}
