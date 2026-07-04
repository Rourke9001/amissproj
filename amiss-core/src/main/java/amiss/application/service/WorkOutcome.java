package amiss.application.service;

/**
 * The outcome of {@link StatsService#work()} (was the body of {@code StatsService.workMain()}).
 *
 * @param status           {@link Status#OK} when the shift was worked; otherwise why not
 * @param remainingMinutes the clock after the attempt ({@code -1} when nothing was spent)
 * @param cash             cash after the attempt, computed the same way {@code setCash} does
 *                         ({@code -1} when no cash changed hands)
 * @param jobName          the player's current job (or {@code "Unemployed"})
 * @param hourlyWage       the job's hourly wage
 * @param debtDocked       {@code true} if an outstanding debt took R10 out of the earnings
 */
public record WorkOutcome(Status status, int remainingMinutes, int cash, String jobName, int hourlyWage,
        boolean debtDocked) {

    public enum Status {
        /** Worked the shift: time and cash were charged/paid. */
        OK,
        /** The job's dress code isn't met; nothing was charged. */
        UNDERDRESSED,
        /** Working would push the clock below zero; nothing was charged. */
        INSUFFICIENT_TIME,
        /** A persistence failure prevented the shift. */
        FAILED
    }
}
