package amiss.api.persistence.jpa;

import amiss.domain.model.ApplianceItem;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA mapping of {@code tblsave} — one saved game (KAN-52). An account
 * ({@code tbluser.name}) owns any number of saves, each with independent progress
 * and its own four win-goal targets chosen at creation.
 *
 * <p>{@code experience} and {@code dependability} are hidden stats: consult them in
 * rules, never expose them on a wire DTO (see the milestone spec). {@code createdAt}
 * / {@code updatedAt} are database-maintained ({@code DEFAULT CURRENT_TIMESTAMP(6)}
 * / {@code ON UPDATE}), hence not insertable/updatable here.
 */
@Entity
@Table(name = "tblsave")
public class SaveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner", length = 50, nullable = false)
    private String owner;

    @Column(name = "label", length = 50, nullable = false)
    private String label;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "xpos", nullable = false)
    private int xpos;

    @Column(name = "ypos", nullable = false)
    private int ypos;

    /** Minutes left this week (see V3 — never hours, never floats). */
    @Column(name = "`time`", nullable = false)
    private int time;

    @Column(name = "round", nullable = false)
    private int round;

    @Column(name = "cash", nullable = false)
    private int cash;

    @Column(name = "bank", nullable = false)
    private int bank;

    @Column(name = "debt", nullable = false)
    private int debt;

    /** 1 = rent owed, 0 = paid (legacy convention carried over). */
    @Column(name = "rent", nullable = false)
    private int rent;

    /** Weeks of food stored. */
    @Column(name = "eat", nullable = false)
    private int eat;

    @Column(name = "clothing", nullable = false)
    private int clothing;

    /** Catalog id of the held job; null = unemployed. */
    @Column(name = "job_id")
    private Integer jobId;

    /** Hired-at wage (economy-adjusted), null while unemployed. */
    @Column(name = "wage")
    private Integer wage;

    @Column(name = "happiness", nullable = false)
    private int happiness;

    @Column(name = "experience", nullable = false)
    private int experience;

    @Column(name = "dependability", nullable = false)
    private int dependability;

    /** Degree currently studied at Hi-Tech U; null = not enrolled. */
    @Column(name = "current_course_id")
    private Integer currentCourseId;

    /** Study sessions completed toward the current course. */
    @Column(name = "eduprog", nullable = false)
    private int eduprog;

    @Column(name = "goal_wealth", nullable = false)
    private int goalWealth;

    @Column(name = "goal_happiness", nullable = false)
    private int goalHappiness;

    @Column(name = "goal_education", nullable = false)
    private int goalEducation;

    @Column(name = "goal_career", nullable = false)
    private int goalCareer;

    /** 0/1; sticky once set (win checked at week rollover). */
    @Column(name = "won", nullable = false)
    private int won;

    /** Hidden economy trend, -3..+3 (KAN-48). Never expose on a wire DTO. */
    @Column(name = "economy_index", nullable = false)
    private byte economyIndex;

    /** Hidden economy reading, -30..+90 (KAN-48): price = base + base*reading/60. */
    @Column(name = "economy_reading", nullable = false)
    private short economyReading;

    /** 1 = Fast Food bought last turn (feeds this turn only, never banked). */
    @Column(name = "ate_fast_food_last_turn", nullable = false)
    private int ateFastFoodLastTurn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tblsave_appliance", joinColumns = @JoinColumn(name = "save_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "appliance", nullable = false, length = 20)
    private Set<ApplianceItem> ownedAppliances = new HashSet<>();

    protected SaveEntity() {
        // JPA
    }

    /**
     * @param timeMinutes round 1's budget. Comes from {@code ActionCosts.baseWeekMinutes()} —
     *     the same source every later week is set from at rollover — so a deployment that
     *     overrides {@code amiss.costs.base-week-minutes} cannot end up with a first week of
     *     a different length to the rest (KAN-23).
     */
    public SaveEntity(String owner, String label,
            int goalWealth, int goalHappiness, int goalEducation, int goalCareer, int timeMinutes) {
        this.owner = owner;
        this.label = label;
        this.goalWealth = goalWealth;
        this.goalHappiness = goalHappiness;
        this.goalEducation = goalEducation;
        this.goalCareer = goalCareer;
        this.time = timeMinutes;
        this.round = 1;
        this.cash = 100;
        this.rent = 1;
        this.clothing = 1;
        this.experience = 10;
        this.dependability = 20;
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getXpos() {
        return xpos;
    }

    public void setXpos(int xpos) {
        this.xpos = xpos;
    }

    public int getYpos() {
        return ypos;
    }

    public void setYpos(int ypos) {
        this.ypos = ypos;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getCash() {
        return cash;
    }

    public void setCash(int cash) {
        this.cash = cash;
    }

    public int getBank() {
        return bank;
    }

    public void setBank(int bank) {
        this.bank = bank;
    }

    public int getDebt() {
        return debt;
    }

    public void setDebt(int debt) {
        this.debt = debt;
    }

    public int getRent() {
        return rent;
    }

    public void setRent(int rent) {
        this.rent = rent;
    }

    public int getEat() {
        return eat;
    }

    public void setEat(int eat) {
        this.eat = eat;
    }

    public int getClothing() {
        return clothing;
    }

    public void setClothing(int clothing) {
        this.clothing = clothing;
    }

    public Integer getJobId() {
        return jobId;
    }

    public void setJobId(Integer jobId) {
        this.jobId = jobId;
    }

    public Integer getWage() {
        return wage;
    }

    public void setWage(Integer wage) {
        this.wage = wage;
    }

    public int getHappiness() {
        return happiness;
    }

    public void setHappiness(int happiness) {
        this.happiness = happiness;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public int getDependability() {
        return dependability;
    }

    public void setDependability(int dependability) {
        this.dependability = dependability;
    }

    public Integer getCurrentCourseId() {
        return currentCourseId;
    }

    public void setCurrentCourseId(Integer currentCourseId) {
        this.currentCourseId = currentCourseId;
    }

    public int getEduprog() {
        return eduprog;
    }

    public void setEduprog(int eduprog) {
        this.eduprog = eduprog;
    }

    public int getGoalWealth() {
        return goalWealth;
    }

    public int getGoalHappiness() {
        return goalHappiness;
    }

    public int getGoalEducation() {
        return goalEducation;
    }

    public int getGoalCareer() {
        return goalCareer;
    }

    public int getWon() {
        return won;
    }

    public void setWon(int won) {
        this.won = won;
    }

    public byte getEconomyIndex() {
        return economyIndex;
    }

    public void setEconomyIndex(byte economyIndex) {
        this.economyIndex = economyIndex;
    }

    public short getEconomyReading() {
        return economyReading;
    }

    public void setEconomyReading(short economyReading) {
        this.economyReading = economyReading;
    }

    public boolean isAteFastFoodLastTurn() {
        return ateFastFoodLastTurn != 0;
    }

    public void setAteFastFoodLastTurn(boolean ateFastFoodLastTurn) {
        this.ateFastFoodLastTurn = ateFastFoodLastTurn ? 1 : 0;
    }

    public Set<ApplianceItem> getOwnedAppliances() {
        return ownedAppliances;
    }
}
