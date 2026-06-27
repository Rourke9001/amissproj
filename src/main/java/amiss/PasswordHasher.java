package amiss;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing with BCrypt (jbcrypt).
 *
 * <p>Stores salted, adaptive hashes instead of plaintext. To keep accounts
 * created before hashing was introduced working, {@link #matches} also accepts
 * a legacy plaintext value; callers should re-hash whenever {@link #needsRehash}
 * reports {@code true}, transparently upgrading the row on the next login.
 */
public final class PasswordHasher {

    private PasswordHasher() {
    }

    /** Hashes a plaintext password with a fresh per-password random salt. */
    public static String hash(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt());
    }

    /** A stored value is a BCrypt hash if it is 60 chars with the $2a/$2b/$2y prefix. */
    public static boolean isHashed(String stored) {
        return stored != null
                && stored.length() == 60
                && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    /**
     * Verifies {@code plaintext} against a stored value, accepting either a
     * BCrypt hash or a legacy plaintext value (for pre-hashing accounts).
     */
    public static boolean matches(String plaintext, String stored) {
        if (stored == null) {
            return false;
        }
        if (isHashed(stored)) {
            return BCrypt.checkpw(plaintext, stored);
        }
        return stored.equals(plaintext); // legacy plaintext account
    }

    /** True if the stored value is legacy plaintext that should be upgraded to a hash. */
    public static boolean needsRehash(String stored) {
        return !isHashed(stored);
    }
}
