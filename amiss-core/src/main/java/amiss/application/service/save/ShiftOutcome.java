package amiss.application.service.save;

/**
 * Result of a work session (KAN-53). {@code warning} = dependability has slipped
 * 3–5 points below the job's requirement (the boss grumbles, work still happens);
 * {@link Status#FIRED} = it fell more than 5 below and the job is gone. {@code pay}
 * is the gross session payout, {@code netPaid} what reached the pocket after any
 * debt garnish (half of pay toward debt + R2 interest fee).
 */
public record ShiftOutcome(Status status, boolean warning, int pay, int netPaid,
        int garnished, int minutesCharged, int remainingMinutes, String jobName) {

    public enum Status {
        OK, NO_JOB, UNDERDRESSED, FIRED, WEEK_OVER
    }
}
