package amiss.application.service;

/**
 * The outcome of {@link UniversityService#enroll()} (was
 * {@code UniversityGUI.btnEnrollActionPerformed}).
 *
 * @param status {@link Status#OK} when enrollment happened; otherwise why not
 * @param cash   cash after the attempt
 */
public record EnrollOutcome(Status status, int cash) {

    public enum Status {
        /** Enrolled: {@link UniversityService#ENROLL_FEE} was charged and progress reset. */
        OK,
        /**
         * Already enrolled in a degree. Swing's Enroll button is hidden whenever progress is
         * non-zero, so this is a defensive guard the API needs but Swing never reaches.
         */
        ALREADY_ENROLLED,
        /** Every degree is already complete; Swing's Enroll button is hidden in this state too. */
        EDUCATION_COMPLETE,
        /** Cash is below the enrollment fee; nothing was charged. */
        INSUFFICIENT_CASH,
        /** The week is used up; nothing was charged. */
        WEEK_OVER,
        /** A persistence failure prevented enrollment. */
        FAILED
    }
}
