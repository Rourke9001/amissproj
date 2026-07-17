package amiss.api.error;

/** Thrown when buying an appliance/book the save already owns (→ 409). */
public class ApplianceAlreadyOwnedException extends RuntimeException {

    public ApplianceAlreadyOwnedException(String item) {
        super("Appliance " + item + " is already owned");
    }
}
