package amiss.domain.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The full mutable state of one saved game (KAN-53) — the new-layer counterpart of the
 * retired per-username {@code User}/stats split. Services mutate an instance and hand it
 * back to {@code SaveRepository.update} wholesale.
 *
 * <p>{@code experience}, {@code dependability}, {@code economyIndex}, and {@code economyReading}
 * are <em>hidden</em> stats: rules read them, but they must never be exposed on a wire DTO
 * (milestone spec). {@code jobId} / {@code currentCourseId} are catalog ids, null when
 * unemployed / not enrolled.
 *
 * <p>{@code eat} (KAN-23) specifically means weeks of Fresh Food stored — it is distinct
 * from {@code ateFastFoodLastTurn}, which feeds exactly one turn and is never banked.
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
    private int casualWeeks;
    private int dressWeeks;
    private int businessWeeks;
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
    private byte economyIndex;
    private short economyReading;
    private Integer wage;
    private boolean ateFastFoodLastTurn;
    private final Set<ApplianceItem> ownedAppliances;
    private int relaxation;
    private boolean relaxedThisTurn;

    public SaveState(long id, String owner, String label, int xpos, int ypos, int timeMinutes,
            int round, int cash, int bank, int debt, int rent, int eat,
            int casualWeeks, int dressWeeks, int businessWeeks,
            Integer jobId, int happiness, int experience, int dependability,
            Integer currentCourseId, int eduprog,
            int goalWealth, int goalHappiness, int goalEducation, int goalCareer, boolean won,
            byte economyIndex, short economyReading, Integer wage,
            boolean ateFastFoodLastTurn, Set<ApplianceItem> ownedAppliances,
            int relaxation, boolean relaxedThisTurn) {
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
        this.casualWeeks = casualWeeks;
        this.dressWeeks = dressWeeks;
        this.businessWeeks = businessWeeks;
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
        this.wage = wage;
        this.ateFastFoodLastTurn = ateFastFoodLastTurn;
        this.ownedAppliances = new HashSet<>(ownedAppliances);
        this.relaxation = relaxation;
        this.relaxedThisTurn = relaxedThisTurn;
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

    public int casualWeeks() {
        return casualWeeks;
    }

    public void setCasualWeeks(int casualWeeks) {
        this.casualWeeks = casualWeeks;
    }

    public int dressWeeks() {
        return dressWeeks;
    }

    public void setDressWeeks(int dressWeeks) {
        this.dressWeeks = dressWeeks;
    }

    public int businessWeeks() {
        return businessWeeks;
    }

    public void setBusinessWeeks(int businessWeeks) {
        this.businessWeeks = businessWeeks;
    }

    /**
     * Whether this save has at least one week of clothing left in {@code requiredLevel}
     * (1=Casual, 2=Dress, 3=Business) <strong>or a higher category</strong> — a Business
     * Suit satisfies a Casual-requiring job, but not vice-versa. {@code 0} (no requirement)
     * always passes.
     */
    public boolean hasClothingLevel(int requiredLevel) {
        return switch (requiredLevel) {
            case 1 -> casualWeeks > 0 || dressWeeks > 0 || businessWeeks > 0;
            case 2 -> dressWeeks > 0 || businessWeeks > 0;
            case 3 -> businessWeeks > 0;
            default -> true;
        };
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

    public byte economyIndex() {
        return economyIndex;
    }

    public void setEconomyIndex(byte economyIndex) {
        this.economyIndex = economyIndex;
    }

    public short economyReading() {
        return economyReading;
    }

    public void setEconomyReading(short economyReading) {
        this.economyReading = economyReading;
    }

    /** Hired-at wage (economy-adjusted), null while unemployed. */
    public Integer wage() {
        return wage;
    }

    public void setWage(Integer wage) {
        this.wage = wage;
    }

    /** True if Fast Food was bought last turn — feeds exactly this turn, never banked. */
    public boolean ateFastFoodLastTurn() {
        return ateFastFoodLastTurn;
    }

    public void setAteFastFoodLastTurn(boolean ateFastFoodLastTurn) {
        this.ateFastFoodLastTurn = ateFastFoodLastTurn;
    }

    public Set<ApplianceItem> ownedAppliances() {
        return Collections.unmodifiableSet(ownedAppliances);
    }

    public boolean owns(ApplianceItem item) {
        return ownedAppliances.contains(item);
    }

    public void grantAppliance(ApplianceItem item) {
        ownedAppliances.add(item);
    }

    public int relaxation() {
        return relaxation;
    }

    public void setRelaxation(int relaxation) {
        this.relaxation = relaxation;
    }

    /** Applies {@code delta} clamped to the wiki's 10..50 band — used for both the Relax
     *  action's +3 gain and the weekly -1 decay. */
    public void addRelaxation(int delta) {
        this.relaxation = Math.max(10, Math.min(50, relaxation + delta));
    }

    public boolean relaxedThisTurn() {
        return relaxedThisTurn;
    }

    public void setRelaxedThisTurn(boolean relaxedThisTurn) {
        this.relaxedThisTurn = relaxedThisTurn;
    }
}
