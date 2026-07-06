package amiss.application.service;

/**
 * The outcome of {@link UniversityService#study()} (was
 * {@code UniversityGUI.btnStudyActionPerformed}).
 *
 * @param status           {@link Status#OK}/{@link Status#DEGREE_COMPLETED} when a study
 *                         session happened; otherwise why not
 * @param remainingMinutes the clock after the attempt ({@code -1} when nothing was spent)
 * @param progress         study count toward the current degree after the attempt
 *                         ({@link UniversityService#STUDIES_PER_DEGREE} completes it; 0 once
 *                         a degree completes and resets)
 * @param educationLevel   the player's education level after the attempt
 * @param degreeCompleted  the name of the degree just finished, or {@code null} unless
 *                         {@code status == DEGREE_COMPLETED}
 */
public record StudyOutcome(Status status, int remainingMinutes, int progress, int educationLevel,
        String degreeCompleted) {

    public enum Status {
        /** Studied: progress advanced but the degree isn't complete yet. */
        OK,
        /** Studied and completed the degree: education level advanced, progress reset. */
        DEGREE_COMPLETED,
        /**
         * Not currently enrolled. Swing's Study button is hidden whenever progress is zero,
         * so this is a defensive guard the API needs but Swing never reaches.
         */
        NOT_ENROLLED,
        /** Every degree is already complete; Swing's Study button is hidden in this state too. */
        EDUCATION_COMPLETE,
        /** Studying would push the clock below zero; nothing was charged. */
        INSUFFICIENT_TIME,
        /** The week is used up; nothing was charged. */
        WEEK_OVER,
        /** A persistence failure prevented the study session. */
        FAILED
    }
}
