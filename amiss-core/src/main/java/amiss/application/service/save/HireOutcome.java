package amiss.application.service.save;

import java.util.List;

/**
 * Result of an Employment Office application (KAN-53). Rejections carry every reason
 * the officer names — the ONLY channel through which a player ever learns about a
 * job's (hidden) requirements. A charged rejection is a normal outcome, not an error.
 */
public record HireOutcome(Status status, List<Reason> reasons, int minutesCharged,
        int remainingMinutes, String jobName, int wage) {

    public enum Status {
        HIRED, REJECTED, UNKNOWN_JOB, WEEK_OVER
    }

    /** What the officer says; dependability shortfalls in weeks 1–4 surface as NO_OPENINGS. */
    public enum Reason {
        NOT_ENOUGH_EDUCATION, NOT_ENOUGH_EXPERIENCE, POOR_WORK_HISTORY, NO_OPENINGS
    }

    static HireOutcome hired(int minutesCharged, int remaining, String jobName, int wage) {
        return new HireOutcome(Status.HIRED, List.of(), minutesCharged, remaining, jobName, wage);
    }

    static HireOutcome rejected(List<Reason> reasons, int minutesCharged, int remaining, String jobName) {
        return new HireOutcome(Status.REJECTED, List.copyOf(reasons), minutesCharged, remaining, jobName, -1);
    }
}
