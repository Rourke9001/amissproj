package amiss.api.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA mapping of {@code tblsave_turndowns} — a job the Employment Office refused with
 * "No Openings" (KAN-52). The block only applies while {@code round} matches the
 * save's current round; stale rows are overwritten or ignored, never a gameplay rule.
 */
@Entity
@Table(name = "tblsave_turndowns")
@IdClass(SaveTurndownId.class)
public class SaveTurndownEntity {

    @Id
    @Column(name = "save_id")
    private Long saveId;

    @Id
    @Column(name = "job_id")
    private Integer jobId;

    @Column(name = "round", nullable = false)
    private int round;

    protected SaveTurndownEntity() {
        // JPA
    }

    public SaveTurndownEntity(Long saveId, Integer jobId, int round) {
        this.saveId = saveId;
        this.jobId = jobId;
        this.round = round;
    }

    public Long getSaveId() {
        return saveId;
    }

    public Integer getJobId() {
        return jobId;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }
}
