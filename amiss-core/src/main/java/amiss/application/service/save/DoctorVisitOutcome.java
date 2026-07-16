package amiss.application.service.save;

/**
 * What a Doctor Visit did (KAN-23, wiki Doctor Visit page): +10h, -4 happiness, and a
 * cash cost tiered by how much cash the player has on hand. {@code none()} for every
 * turn where no trigger fired, or where cash was 0 (the wiki bypasses the event
 * entirely rather than charging a bankrupt player).
 */
public record DoctorVisitOutcome(boolean triggered, int minutesLost, int happinessLost, int cashLost) {

    public static DoctorVisitOutcome none() {
        return new DoctorVisitOutcome(false, 0, 0, 0);
    }
}
