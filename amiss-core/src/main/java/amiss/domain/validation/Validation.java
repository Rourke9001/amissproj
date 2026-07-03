package amiss.domain.validation;

import java.util.regex.Pattern;

/**
 * Small input-validation helpers for user-supplied form fields.
 *
 * <p>Keeps the rules in one place so the login / sign-up screens (and any
 * future screens) validate consistently before values reach the database.
 * SQL injection is already handled by the parameterised JDBC layer; these
 * checks reject nonsensical input early and give the user a clear message.
 */
public final class Validation {

    /** Usernames: 1-50 chars (matches the {@code tbluser.name} column), letters/digits/underscore. */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{1,50}$");

    /** Minimum password length accepted on sign-up. */
    public static final int MIN_PASSWORD_LENGTH = 4;

    private Validation() {
    }

    /**
     * @return {@code true} if the username is non-null and matches the allowed format
     */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    /**
     * @return {@code true} if the password is non-null and meets the minimum length
     */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    /**
     * Parses {@code text} as an int, returning {@code defaultValue} when it is
     * null or not a valid integer instead of throwing {@link NumberFormatException}.
     */
    public static int parseIntOrDefault(String text, int defaultValue) {
        if (text == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
