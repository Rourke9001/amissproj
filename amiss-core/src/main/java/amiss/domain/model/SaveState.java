package amiss.domain.model;

/**
 * The full mutable state of one saved game (KAN-53) — the new-layer counterpart of the
 * retired per-username {@code User}/stats split. Services mutate an instance and hand it
 * back to {@code SaveRepository.update} wholesale.
 *
 * <p>{@code experience}, {@code dependability}, {@code economyIndex}, and {@code economyReading}
 * are <em>hidden</em> stats: rules read them, but they must never be exposed on a wire DTO
 * (milestone spec). {@code jobId} / {@code currentCourseId} are catalog ids, null when
 * unemployed / not enrolled.
 */
public class SaveState {

    private final long id;
    private final String owner;
    private String label;
    private int xpos;
    private int ypos;
    private int timeMinutes;
    private int round;
    private int cash;
    private int bank;
    private int debt;
    private int rent;
    private int eat;
    private int clothing;
    private Integer jobId;
    private int happiness;
    private int experience;
    private int dependability;
    private Integer currentCourseId;
    private int eduprog;
    private final int goalWealth;
    private final int goalHappiness;
    private final int goalEducation;
    private final int goalCareer;
    private boolean won;
    private int economyIndex;
    private int economyReading;

    public SaveState(long id, String owner, String label, int xpos, int ypos, int timeMinutes,
            int round, int cash, int bank, int debt, int rent, int eat, int clothing,
            Integer jobId, int happiness, int experience, int dependability,
            Integer currentCourseId, int eduprog,
            int goalWealth, int goalHappiness, int goalEducation, int goalCareer, boolean won,
            int economyIndex, int economyReading) {
        this.id = id;
        this.owner = owner;
        this.label = label;
        this.xpos = xpos;
        this.ypos = ypos;
        this.timeMinutes = timeMinutes;
        this.round = round;
        this.cash = cash;
        this.bank = bank;
        this.debt = debt;
        this.rent = rent;
        this.eat = eat;
        this.clothing = clothing;
        this.jobId = jobId;
        this.happiness = happiness;
        this.experience = experience;
        this.dependability = dependability;
        this.currentCourseId = currentCourseId;
        this.eduprog = eduprog;
        this.goalWealth = goalWealth;
        this.goalHappiness = goalHappiness;
        this.goalEducation = goalEducation;
        this.goalCareer = goalCareer;
        this.won = won;
        this.economyIndex = economyIndex;
        this.economyReading = economyReading;
    }

    public long id() {
        return id;
    }

    public String owner() {
        return owner;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int xpos() {
        return xpos;
    }

    public int ypos() {
        return ypos;
    }

    public void setPos(int xpos, int ypos) {
        this.xpos = xpos;
        this.ypos = ypos;
    }

    public int timeMinutes() {
        return timeMinutes;
    }

    public void setTimeMinutes(int timeMinutes) {
        this.timeMinutes = timeMinutes;
    }

    /** Charges up to {@code minutes}, clamped to the clock; returns what was charged. */
    public int spendUpTo(int minutes) {
        int charged = Math.min(minutes, timeMinutes);
        timeMinutes -= charged;
        return charged;
    }

    public boolean weekOver() {
        return timeMinutes == 0;
    }

    public int round() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int cash() {
        return cash;
    }

    public void setCash(int cash) {
        this.cash = cash;
    }

    public int bank() {
        return bank;
    }

    public void setBank(int bank) {
        this.bank = bank;
    }

    public int debt() {
        return debt;
    }

    public void setDebt(int debt) {
        this.debt = debt;
    }

    public int rent() {
        return rent;
    }

    public void setRent(int rent) {
        this.rent = rent;
    }

    public int eat() {
        return eat;
    }

    public void setEat(int eat) {
        this.eat = eat;
    }

    public int clothing() {
        return clothing;
    }

    public void setClothing(int clothing) {
        this.clothing = clothing;
    }

    public Integer jobId() {
        return jobId;
    }

    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }

    public boolean employed() {
        return jobId != null;
    }

    public int happiness() {
        return happiness;
    }

    public void addHappiness(int delta) {
        this.happiness = Math.max(0, happiness + delta);
    }

    public int experience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public int dependability() {
        return dependability;
    }

    public void setDependability(int dependability) {
        this.dependability = dependability;
    }

    public Integer currentCourseId() {
        return currentCourseId;
    }

    public void setCurrentCourseId(Integer currentCourseId) {
        this.currentCourseId = currentCourseId;
    }

    public int eduprog() {
        return eduprog;
    }

    public void setEduprog(int eduprog) {
        this.eduprog = eduprog;
    }

    public int goalWealth() {
        return goalWealth;
    }

    public int goalHappiness() {
        return goalHappiness;
    }

    public int goalEducation() {
        return goalEducation;
    }

    public int goalCareer() {
        return goalCareer;
    }

    public boolean won() {
        return won;
    }

    public void setWon(boolean won) {
        this.won = won;
    }

    public int economyIndex() {
        return economyIndex;
    }

    public void setEconomyIndex(int economyIndex) {
        this.economyIndex = economyIndex;
    }

    public int economyReading() {
        return economyReading;
    }

    public void setEconomyReading(int economyReading) {
        this.economyReading = economyReading;
    }
}
