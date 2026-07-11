package amiss.application.service.save;

/**
 * The outcome of {@link TravelService#moveTo} (KAN-53), the save-scoped port of the legacy
 * {@code amiss.application.service.MoveResult}.
 *
 * @param status           {@link Status#OK} when the move happened; otherwise why it did not
 * @param steps            the ring distance to the target (informational even on rejection)
 * @param minutesCharged   what the move cost; {@code 0} when the spend was rejected. A move
 *                         that lands the clock exactly on zero is charged but ends the week
 *                         ({@link Status#WEEK_OVER}) without moving — the position stays put and
 *                         the coming rollover resets it to home anyway.
 * @param remainingMinutes the clock after the attempt
 */
public record MoveResult(Status status, int steps, int minutesCharged, int remainingMinutes) {

    public enum Status {
        /** Moved: time charged and the new position persisted. */
        OK,
        /** The walk + entry cost exceeds the time left; nothing changed. */
        INSUFFICIENT_TIME,
        /** The week is (or just became) used up; the position did not change. */
        WEEK_OVER
    }
}
