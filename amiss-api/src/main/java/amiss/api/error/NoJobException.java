package amiss.api.error;

/** Thrown when working is attempted while the player holds no job (→ 409). */
public class NoJobException extends RuntimeException {

    public NoJobException(String username) {
        super("Player '" + username + "' is unemployed");
    }
}
