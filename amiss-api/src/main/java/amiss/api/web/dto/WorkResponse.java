package amiss.api.web.dto;

/**
 * A work-shift attempt (KAN-54). {@code status} is {@code OK} or {@code FIRED} (dependability
 * fell too far below the job's requirement — no pay, no time charged); {@code warning} means
 * the boss is unhappy but the shift still happened. {@code netPaid}/{@code garnished} reflect
 * any pay docked toward an outstanding rent debt.
 */
public record WorkResponse(String status, boolean warning, String job, int pay, int netPaid,
        int garnished, int minutesCharged, SaveStateDto state) {
}
