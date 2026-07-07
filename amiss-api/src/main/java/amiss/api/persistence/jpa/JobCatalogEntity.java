package amiss.api.persistence.jpa;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Set;

/**
 * JPA mapping of {@code tbljob} — the wiki-accurate 39-job catalog (KAN-52).
 * Reference data seeded by V5; the game never writes rows here.
 *
 * <p>Job names repeat across workplaces (Manager, Janitor, …), so the identity is the
 * surrogate {@code id}; {@code (location, job)} is unique. Required degrees live in
 * {@code tbljob_degrees}, mapped as an eager element collection (0–2 rows per job).
 *
 * <p>The requirement columns exist to decide an application server-side — they must
 * never reach a wire DTO (hidden-requirements rule, see the milestone spec).
 */
@Entity
@Table(name = "tbljob")
public class JobCatalogEntity {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "job", length = 50, nullable = false)
    private String job;

    @Column(name = "location", length = 50, nullable = false)
    private String location;

    @Column(name = "wage", nullable = false)
    private int wage;

    @Column(name = "req_experience", nullable = false)
    private int reqExperience;

    /** Listed value; a listed 10 truly requires 0 (wiki anti-frustration rule, applied in core). */
    @Column(name = "req_dependability", nullable = false)
    private int reqDependability;

    @Column(name = "req_clothing", nullable = false)
    private int reqClothing;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tbljob_degrees", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "degree_id", nullable = false)
    private Set<Integer> requiredDegreeIds;

    protected JobCatalogEntity() {
        // JPA
    }

    public Integer getId() {
        return id;
    }

    public String getJob() {
        return job;
    }

    public String getLocation() {
        return location;
    }

    public int getWage() {
        return wage;
    }

    public int getReqExperience() {
        return reqExperience;
    }

    public int getReqDependability() {
        return reqDependability;
    }

    public int getReqClothing() {
        return reqClothing;
    }

    public Set<Integer> getRequiredDegreeIds() {
        return requiredDegreeIds;
    }
}
