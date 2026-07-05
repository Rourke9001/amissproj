package amiss.api.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA mapping of {@code tbluser} (the player's saved state), validated against the
 * Flyway-owned schema ({@code ddl-auto=validate}, KAN-33).
 *
 * <p>{@code name} is the natural primary key — no surrogate id, matching the schema.
 * All columns are {@code NOT NULL} in the database, so every non-key field is a
 * primitive. {@code time} is a reserved SQL word; the backtick-wrapped column name
 * tells Hibernate to quote it for MySQL, exactly as the Flyway migration does.
 *
 * <p>Backed by {@link UserJpaRepository} / {@link JpaUserRepository}, the {@code
 * UserRepository} port adapter (KAN-34). The Swing client still reads/writes {@code
 * tbluser} through the core's {@code JdbcUserRepository}.
 */
@Entity
@Table(name = "tbluser")
public class UserEntity {

    @Id
    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "password", length = 60, nullable = false)
    private String password;

    @Column(name = "xpos", nullable = false)
    private int xpos;

    @Column(name = "ypos", nullable = false)
    private int ypos;

    @Column(name = "`time`", nullable = false)
    private int time;

    @Column(name = "cash", nullable = false)
    private int cash;

    @Column(name = "round", nullable = false)
    private int round;

    @Column(name = "job", length = 50, nullable = false)
    private String job;

    @Column(name = "clothing", nullable = false)
    private int clothing;

    @Column(name = "rent", nullable = false)
    private int rent;

    @Column(name = "eat", nullable = false)
    private int eat;

    @Column(name = "debt", nullable = false)
    private int debt;

    @Column(name = "bank", nullable = false)
    private int bank;

    protected UserEntity() {
        // JPA
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public int getCash() {
        return cash;
    }

    public void setCash(int cash) {
        this.cash = cash;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public int getClothing() {
        return clothing;
    }

    public void setClothing(int clothing) {
        this.clothing = clothing;
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

    public int getDebt() {
        return debt;
    }

    public void setDebt(int debt) {
        this.debt = debt;
    }

    public int getBank() {
        return bank;
    }

    public void setBank(int bank) {
        this.bank = bank;
    }
}
