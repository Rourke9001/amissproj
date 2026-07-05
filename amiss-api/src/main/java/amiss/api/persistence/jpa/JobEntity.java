package amiss.api.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA mapping of {@code tbljobs} (the jobs the game offers and their education /
 * salary / location / clothing requirements), validated against the Flyway-owned
 * schema ({@code ddl-auto=validate}, KAN-33). Reference data — the game never
 * inserts or updates rows in this table.
 *
 * <p>{@code job} is the natural primary key — no surrogate id, matching the schema.
 *
 * <p>No repository exists yet for this entity — that lands in a later PR (KAN-34).
 * The running app still reads {@code tbljobs} through
 * {@code amiss.infrastructure.persistence.jdbc.JdbcJobRepository}.
 */
@Entity
@Table(name = "tbljobs")
public class JobEntity {

    @Id
    @Column(name = "job", length = 50)
    private String job;

    @Column(name = "education", nullable = false)
    private int education;

    @Column(name = "salary", nullable = false)
    private int salary;

    @Column(name = "location", length = 50, nullable = false)
    private String location;

    @Column(name = "clothing", nullable = false)
    private int clothing;

    protected JobEntity() {
        // JPA
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public int getEducation() {
        return education;
    }

    public void setEducation(int education) {
        this.education = education;
    }

    public int getSalary() {
        return salary;
    }

    public void setSalary(int salary) {
        this.salary = salary;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getClothing() {
        return clothing;
    }

    public void setClothing(int clothing) {
        this.clothing = clothing;
    }
}
