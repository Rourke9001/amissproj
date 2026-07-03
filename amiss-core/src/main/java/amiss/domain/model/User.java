/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amiss.domain.model;

/**
 * User Object Class
 * @author The Rourke
 */
public class User {

    private String user, job;
    private int round, xpos, ypos, time, cash, clothing, rent, eat, debt;

    /**
     * Creates the user object
     * @param user The user's name
     * @param xpos The user's x position
     * @param ypos The user's y position
     * @param time The user's remaining time 
     * @param cash The user's cash on hand
     * @param round The user's current round
     * @param job The user's current job
     * @param clothing The user's current clothes
     * @param rent The user's rent status
     * @param eat The user's amount of food
     * @param debt The user's debt
     */
    public User(String user, int xpos, int ypos, int time, int cash, int round, String job, int clothing, int rent, int eat, int debt) {
        this.user = user;
        this.xpos = xpos;
        this.ypos = ypos;
        this.time = time;
        this.cash = cash;
        this.round = round;
        this.job = job;
        this.clothing = clothing;
        this.rent = rent;
        this.eat = eat;
        this.debt = debt;
    }

    /**
     * Returns the User's username
     * @return Returns The User's username
     */
    public String getUser() {
        return user;
    }

    /**
     * Sets the User's username
     * @param user the users username
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Returns the User's Job
     * @return Returns the User's Job
     */
    public String getJob() {
        return job;
    }

    /**
     * Sets the User's Job
     * @param job the users current job
     */
    public void setJob(String job) {
        this.job = job;
    }

    /**
     * Returns the User's Current Round
     * @return Returns the User's Current Round
     */
    public int getRound() {
        return round;
    }

    /**
     * Sets the User's Current Round
     * @param round the users current round
     */
    public void setRound(int round) {
        this.round = round;
    }

    /**
     * Returns the User's x-pos
     * @return Returns the User's x-pos
     */
    public int getXpos() {
        return xpos;
    }

    /**
     * Sets the User's x-pos
     * @param xpos the users x pos
     */
    public void setXpos(int xpos) {
        this.xpos = xpos;
    }

    /**
     * Returns the User's y-pos
     * @return Returns the User's y-pos
     */
    public int getYpos() {
        return ypos;
    }

    /**
     * Sets the User's y-pos
     * @param ypos the users y pos
     */
    public void setYpos(int ypos) {
        this.ypos = ypos;
    }

    /**
     * Returns the User's current time
     * @return Returns the User's current time
     */
    public int getTime() {
        return time;
    }

    /**
     * Sets the User's current time
     * @param time the uses current time
     */
    public void setTime(int time) {
        this.time = time;
    }

    /**
     * Returns the User's current cash
     * @return Returns the User's current cash
     */
    public int getCash() {
        return cash;
    }

    /**
     * Sets the User's current cash
     * @param cash users current cash
     */
    public void setCash(int cash) {
        this.cash = cash;
    }

    /**
     * Returns the User's clothes
     * @return Returns the User's clothes
     */
    public int getClothing() {
        return clothing;
    }

    /**
     * Sets the User's clothes
     * @param clothing the users current clothing
     */
    public void setClothing(int clothing) {
        this.clothing = clothing;
    }

    /**
     * Returns the User's Rent Status
     * @return Returns the User's Rent Status
     */
    public int getRent() {
        return rent;
    }

    /**
     * Sets the User's Rent Status
     * @param rent the users rent status
     */
    public void setRent(int rent) {
        this.rent = rent;
    }

    /**
     * Returns the User's amount of food
     * @return Returns the User's amount of food
     */
    public int getEat() {
        return eat;
    }

    /**
     * Sets the User's amount of food
     * @param eat the users amount of food
     */
    public void setEat(int eat) {
        this.eat = eat;
    }

    /**
     * Returns the User's debt amount
     * @return Returns the User's debt amount
     */
    public int getDebt() {
        return debt;
    }

    /**
     * Set the User's debt amount
     * @param debt the users amount of debt
     */
    public void setDebt(int debt) {
        this.debt = debt;
    }

    @Override
    public String toString() {
        return "User{" + "user=" + user + ", job=" + job + ", round=" + round + ", xpos=" + xpos + ", ypos=" + ypos + ", time=" + time + ", cash=" + cash + ", clothing=" + clothing + ", rent=" + rent + ", eat=" + eat + ", debt=" + debt + '}';
    }

}
