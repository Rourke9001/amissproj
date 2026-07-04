package amiss.application.service;

/**
 * The outcome of {@link TimeService#spendMinutes(int)}.
 *
 * @param remainingMinutes the minutes left in the week after the spend (unchanged if the
 *                         spend was rejected)
 * @param rejected         {@code true} if the action would have pushed the clock below zero;
 *                         nothing was persisted
 * @param weekOver         {@code true} if the clock stands at zero — the week is used up and
 *                         only starting a new week can continue play
 */
public record TimeSpend(int remainingMinutes, boolean rejected, boolean weekOver) {
}
