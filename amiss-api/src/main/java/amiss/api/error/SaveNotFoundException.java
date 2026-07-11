package amiss.api.error;

/** Thrown when a {@code {saveId}} path segment names no save at all (→ 404). */
public class SaveNotFoundException extends RuntimeException {

    public SaveNotFoundException(long saveId) {
        super("No save " + saveId);
    }
}
