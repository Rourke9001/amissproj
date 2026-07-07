package amiss.api.persistence.jpa;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link SaveDegreeEntity} (save_id, degree_id). */
public class SaveDegreeId implements Serializable {

    private Long saveId;
    private Integer degreeId;

    protected SaveDegreeId() {
        // JPA
    }

    public SaveDegreeId(Long saveId, Integer degreeId) {
        this.saveId = saveId;
        this.degreeId = degreeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SaveDegreeId other)) {
            return false;
        }
        return Objects.equals(saveId, other.saveId) && Objects.equals(degreeId, other.degreeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(saveId, degreeId);
    }
}
