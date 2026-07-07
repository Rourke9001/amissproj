package amiss.api.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA mapping of {@code tblsave_degrees} — a degree a save has earned (KAN-52).
 * Insert-only from the rules' point of view: degrees can never be lost.
 */
@Entity
@Table(name = "tblsave_degrees")
@IdClass(SaveDegreeId.class)
public class SaveDegreeEntity {

    @Id
    @Column(name = "save_id")
    private Long saveId;

    @Id
    @Column(name = "degree_id")
    private Integer degreeId;

    protected SaveDegreeEntity() {
        // JPA
    }

    public SaveDegreeEntity(Long saveId, Integer degreeId) {
        this.saveId = saveId;
        this.degreeId = degreeId;
    }

    public Long getSaveId() {
        return saveId;
    }

    public Integer getDegreeId() {
        return degreeId;
    }
}
