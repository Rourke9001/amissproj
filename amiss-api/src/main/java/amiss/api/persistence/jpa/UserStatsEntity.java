package amiss.api.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * JPA mapping of {@code tbluserstats} (happiness, education, work experience and
 * current study progress), validated against the Flyway-owned schema
 * ({@code ddl-auto=validate}, KAN-33).
 *
 * <p>{@code name} is both this table's primary key and a foreign key to
 * {@code tbluser(name)} ({@code ON DELETE CASCADE}) — a shared-primary-key 1:1, modelled
 * the standard JPA way with {@link MapsId} deriving this entity's id from the
 * associated {@link UserEntity} rather than a separate generated column.
 *
 * <p>No repository exists yet for this entity — that lands in a later PR (KAN-34).
 * The running app still reads/writes {@code tbluserstats} through
 * {@code amiss.infrastructure.persistence.jdbc.JdbcUserStatsRepository}.
 */
@Entity
@Table(name = "tbluserstats")
public class UserStatsEntity {

    @Id
    @Column(name = "name", length = 50)
    private String name;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "name")
    private UserEntity user;

    @Column(name = "happiness", nullable = false)
    private int happiness;

    @Column(name = "education", nullable = false)
    private int education;

    @Column(name = "work", nullable = false)
    private int work;

    @Column(name = "eduprog", nullable = false)
    private int eduprog;

    protected UserStatsEntity() {
        // JPA
    }

    public String getName() {
        return name;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public int getHappiness() {
        return happiness;
    }

    public void setHappiness(int happiness) {
        this.happiness = happiness;
    }

    public int getEducation() {
        return education;
    }

    public void setEducation(int education) {
        this.education = education;
    }

    public int getWork() {
        return work;
    }

    public void setWork(int work) {
        this.work = work;
    }

    public int getEduprog() {
        return eduprog;
    }

    public void setEduprog(int eduprog) {
        this.eduprog = eduprog;
    }
}
