package amiss.api.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA mapping of {@code tbldegrees} — the 11-degree Hi-Tech U catalog with each
 * degree's single prerequisite (KAN-52). Reference data seeded by V5; the game
 * never writes rows here.
 */
@Entity
@Table(name = "tbldegrees")
public class DegreeEntity {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    /** Degree that must be earned before this course opens; null = available from the start. */
    @Column(name = "prereq_degree_id")
    private Integer prereqDegreeId;

    protected DegreeEntity() {
        // JPA
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getPrereqDegreeId() {
        return prereqDegreeId;
    }
}
