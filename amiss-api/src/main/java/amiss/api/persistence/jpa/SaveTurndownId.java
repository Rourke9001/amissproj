package amiss.api.persistence.jpa;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link SaveTurndownEntity} (save_id, job_id). */
public class SaveTurndownId implements Serializable {

    private Long saveId;
    private Integer jobId;

    protected SaveTurndownId() {
        // JPA
    }

    public SaveTurndownId(Long saveId, Integer jobId) {
        this.saveId = saveId;
        this.jobId = jobId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SaveTurndownId other)) {
            return false;
        }
        return Objects.equals(saveId, other.saveId) && Objects.equals(jobId, other.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(saveId, jobId);
    }
}
