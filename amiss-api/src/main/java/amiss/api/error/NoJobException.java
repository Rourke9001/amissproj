package amiss.api.error;

/** Thrown when working is attempted while the save holds no job (→ 409). */
public class NoJobException extends RuntimeException {

    public NoJobException(long saveId) {
        super("Save " + saveId + " is unemployed");
    }
}
