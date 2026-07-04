package amiss.application.service;
import amiss.domain.model.ActionResult;

import amiss.domain.validation.Validation;
import amiss.application.config.ActionCosts;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The player's money, rent/debt, work experience and happiness, plus the work/eat/shop action
 * orchestration (was {@code Stats}). {@link #work()}, {@link #eat(int)},
 * {@link #buyGroceries(int, int)} and {@link #buyClothes(int, int)} are the typed rule
 * methods shared by Swing and the REST API; {@link #workMain()} and {@link #eatMain(int)}
 * are thin Swing-era formatters over {@link #work()}/{@link #eat(int)} that rebuild the
 * exact same {@link ActionResult} strings. Persistence failures are logged, not shown in
 * the UI.
 */
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);

    private final UserRepository users;
    private final UserStatsRepository stats;
    private final JobService job;
    private final TimeService dist;
    private final FoodService eat;
    private final ActionCosts costs;
    private final String username;

    public StatsService(UserRepository users, UserStatsRepository stats,
            JobService job, TimeService dist, FoodService eat, String username) {
        this(users, stats, job, dist, eat, ActionCosts.defaults(), username);
    }

    public StatsService(UserRepository users, UserStatsRepository stats,
            JobService job, TimeService dist, FoodService eat, ActionCosts costs,
            String username) {
        this.users = users;
        this.stats = stats;
        this.job = job;
        this.dist = dist;
        this.eat = eat;
        this.costs = costs;
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
        WorkOutcome outcome = work();
        switch (outcome.status()) {
            case UNDERDRESSED:
                return ActionResult.message("\n\n" + job.getJobClothes());
            case INSUFFICIENT_TIME:
                return ActionResult.message("\nNot Enough Time");
            default:
                String cashText = outcome.debtDocked()
                        ? "You were deducted R10 for not paying rent \nYou now have R" + outcome.cash()
                        : "You now have R" + outcome.cash();
                String message = "\n" + job.toString() + "\n" + cashText;
                return new ActionResult(message, TimeService.format(outcome.remainingMinutes()),
                        Integer.toString(getCash()));
        }
    }

    /**
     * Checks if the user is properly dressed and has enough time to work, then pays the
     * player's job earnings (docking R10 toward any outstanding debt).
     * @return the typed work outcome
     */
    public WorkOutcome work() {
        String jobName = job.getJob();
        int hourlyWage = job.getEarnings();
        String clothes = job.getJobClothes();
        if (clothes != null) {
            return new WorkOutcome(WorkOutcome.Status.UNDERDRESSED, -1, -1, jobName, hourlyWage, false);
        }

        TimeSpend spend = dist.spendMinutes(costs.workMinutes());
        if (spend.rejected()) {
            return new WorkOutcome(WorkOutcome.Status.INSUFFICIENT_TIME, spend.remainingMinutes(), -1,
                    jobName, hourlyWage, false);
        }

        updateWork();
        boolean debtDocked = getDebt() > 0;
        int cashBefore = getCash();
        int newCash = debtDocked ? cashBefore + hourlyWage - 10 : cashBefore + hourlyWage;
        setCash(hourlyWage);
        return new WorkOutcome(WorkOutcome.Status.OK, spend.remainingMinutes(), newCash, jobName, hourlyWage, debtDocked);
    }

    /**
     * Checks if the user has enough time to eat then updates the users stored food.
     * @param price price of the item being purchased
     * @return the notification text, timer and money values for the screen to render
     */
    public ActionResult eatMain(int price) {
        EatOutcome outcome = eat(price);
        switch (outcome.status()) {
            case INSUFFICIENT_TIME:
                return ActionResult.message("\nNot Enough Time");
            case INSUFFICIENT_CASH:
                return ActionResult.message("\nNot Enough Cash, You only have R" + outcome.cash());
            default:
                String message = "\n" + "You spent R" + price + ", You have R" + outcome.cash() + " left"
                        + "\nThat Was Yummy, One point into happiness";
                return new ActionResult(message, TimeService.format(outcome.remainingMinutes()),
                        Integer.toString(getCash()));
        }
    }

    /**
     * Checks if the user has enough time and cash to eat, then updates stored food and happiness.
     * @param price price of the item being purchased
     * @return the typed eat outcome
     */
    public EatOutcome eat(int price) {
        int food = eat.getFood();
        TimeSpend spend = dist.spendMinutes(costs.eatMinutes());
        if (spend.rejected()) {
            return new EatOutcome(EatOutcome.Status.INSUFFICIENT_TIME, spend.remainingMinutes(), -1, price);
        }

        int cash = getCash();
        if (cash < price) {
            return new EatOutcome(EatOutcome.Status.INSUFFICIENT_CASH, spend.remainingMinutes(), cash, price);
        }

        if (food >= 1) {
            eat.setFood(0);
        } else {
            eat.setFood(1);
        }
        buy(Integer.toString(price));
        updateHappiness();
        return new EatOutcome(EatOutcome.Status.OK, spend.remainingMinutes(), cash - price, price);
    }

    /**
     * Checks if the user has enough time and cash to buy groceries (was the body of
     * {@code MarketGUI.btn1WeeksActionPerformed}), then adds the stored food weeks.
     * @param price price of the grocery pack
     * @param weeks weeks of food the pack is worth
     * @return the typed purchase outcome
     */
    public PurchaseOutcome buyGroceries(int price, int weeks) {
        TimeSpend spend = dist.spendMinutes(costs.shopMinutes());
        if (spend.rejected()) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_TIME, spend.remainingMinutes(), getCash());
        }

        int cash = getCash();
        if (cash < price) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, spend.remainingMinutes(), cash);
        }

        buy(Integer.toString(price));
        eat.setFood(weeks);
        return new PurchaseOutcome(PurchaseOutcome.Status.OK, spend.remainingMinutes(), cash - price);
    }

    /**
     * Checks if the user has enough time and cash to buy clothes (was the body of
     * {@code ClothesStoreGUI.btnCasualActionPerformed}), <strong>then</strong> updates the
     * clothing level. The original Swing handler set the clothing level before this check,
     * so a failed purchase still upgraded the player's clothes for free; this method
     * validates and charges first, fixing that bug.
     * @param level the clothing level purchased
     * @param price price of the clothing item
     * @return the typed purchase outcome
     */
    public PurchaseOutcome buyClothes(int level, int price) {
        TimeSpend clock = dist.spendMinutes(0);
        if (clock.weekOver()) {
            return new PurchaseOutcome(PurchaseOutcome.Status.WEEK_OVER, clock.remainingMinutes(), getCash());
        }

        int cash = getCash();
        if (cash < price) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_CASH, clock.remainingMinutes(), cash);
        }

        TimeSpend spend = dist.spendMinutes(costs.shopMinutes());
        if (spend.rejected()) {
            return new PurchaseOutcome(PurchaseOutcome.Status.INSUFFICIENT_TIME, spend.remainingMinutes(), cash);
        }

        buy(Integer.toString(price));
        job.setClothes(level);
        return new PurchaseOutcome(PurchaseOutcome.Status.OK, spend.remainingMinutes(), cash - price);
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
