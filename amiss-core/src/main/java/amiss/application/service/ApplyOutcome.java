package amiss.application.service;

/**
 * The outcome of {@link JobService#apply(String)} (was {@code JobService.applyForJob(String)}).
 *
 * @param status           {@link Status#HIRED} when the job was taken; otherwise why not
 * @param remainingMinutes the clock after the attempt ({@code -1} when nothing was spent)
 * @param jobName          the job applied for
 * @param hourlyWage       the job's hourly wage ({@code -1} when not applicable, e.g. an
 *                         unknown job or a rejection before hiring)
 */
public record ApplyOutcome(Status status, int remainingMinutes, String jobName, int hourlyWage) {

    public enum Status {
        /** Hired: the job was set. */
        HIRED,
        /**
         * The player's education is below the job's requirement; the 4h application time
         * was still charged (the game rule: applying always takes 4 hours, win or lose).
         */
        INSUFFICIENT_EDUCATION,
        /**
         * {@code jobName} does not exist; checked first, before any charge. Swing's job
         * buttons always send a real job name, so this is a defensive guard the API needs
         * but Swing never reaches.
         */
        UNKNOWN_JOB,
        /** The week is used up; nothing was charged. */
        WEEK_OVER,
        /** Applying would push the clock below zero; nothing was charged. */
        INSUFFICIENT_TIME,
        /** A persistence failure prevented the application. */
        FAILED
    }
}
