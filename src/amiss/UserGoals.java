/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amiss;

/**
 * User Goals Object class
 * @author The Rourke
 */
public class UserGoals {

    private String user;
    private int happiness, education, experience, eduprog;

    /**
     * Creates the user goals object
     * @param user The users name
     * @param happiness The users happiness 
     * @param education the users education
     * @param experience the users work experience
     * @param eduprog the users education progress
     */
    public UserGoals(String user, int happiness, int education, int experience, int eduprog) {
        this.user = user;
        this.happiness = happiness;
        this.education = education;
        this.experience = experience;
        this.eduprog = eduprog;
    }

    
    /**
     * Return the users Username
     * @return returns the users username
     */
    public String getUser() {
        return user;
    }

    /**
     * sets the users username
     * @param user the users username
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * returns the users happiness
     * @return returns the users username
     */
    public int getHappiness() {
        return happiness;
    }

    /**
     * sets the users happiness
     * @param happiness the users happiness 
     */
    public void setHappiness(int happiness) {
        this.happiness = happiness;
    }

    /**
     * returns the users education
     * @return returns the users education
     */
    public int getEducation() {
        return education;
    }

    /**
     * sets the users education
     * @param education the users education
     */
    public void setEducation(int education) {
        this.education = education;
    }

    /**
     * returns the users experience
     * @return returns the users experience
     */
    public int getExperience() {
        return experience;
    }

    /**
     * sets the users experience
     * @param experience the users experience 
     */
    public void setExperience(int experience) {
        this.experience = experience;
    }

    /**
     * returns the users education progress
     * @return returns the users education progress
     */
    public int getEduprog() {
        return eduprog;
    }

    /**
     * sets the users education progress
     * @param eduprog the users education progress
     */
    public void setEduprog(int eduprog) {
        this.eduprog = eduprog;
    }

    @Override
    public String toString() {
        return "UserGoals{" + "user=" + user + ", happiness=" + happiness + ", education=" + education + ", experience=" + experience + ", eduprog=" + eduprog + '}';
    }

}
