package amiss.service;

import amiss.repository.UserRepository;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The player's food items and stored food (was {@code Food}). Swing-free and
 * constructor-injected with its repository and the current username; persistence
 * failures are logged, not shown in the UI.
 */
public class FoodService {

    private static final Logger log = LoggerFactory.getLogger(FoodService.class);

    private final UserRepository users;
    private final String username;

    public FoodService(UserRepository users, String username) {
        this.users = users;
        this.username = username;
    }

    /**
     * Updates the database
     * @param count the amount of food you buying
     */
    public void setFood(int count) {
        int eat = getFood();
        int addFood = count;

        if ((eat == 0 || eat == 1) && count == 1) {
            eat = 1;
        } else {
            addFood = count;
            eat = eat + addFood;
        }

        try {
            users.updateEat(username, eat);
        } catch (SQLException ex) {
            log.warn("Failed to update food", ex);
        }
    }

    /**
     * returns how much stored food the user has
     * @return returns how much stored food the user has
     */
    public int getFood() {
        try {
            return users.getEat(username);
        } catch (SQLException ex) {
            log.warn("Failed to get stored food", ex);
        }
        return 0;
    }

    /**
     * returns if the user ate the previous round
     * @return returns if the user ate the previous round
     */
    public boolean getEat() {
        boolean eaten = false;

        try {
            int num = users.getEat(username);
            if (num != -1) {
                if (num == 0) {
                    eaten = false;
                    return eaten;
                } else {
                    eaten = true;
                    setFood(-1);
                    return eaten;
                }
            }
        } catch (SQLException ex) {
            log.warn("Failed to get has-eaten", ex);
        }

        return false;
    }
}
