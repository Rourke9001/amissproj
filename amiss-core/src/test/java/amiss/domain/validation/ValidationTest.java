package amiss.domain.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure {@link Validation} helpers. No mocks/DB needed.
 */
class ValidationTest {

    // ---- parseIntOrDefault (used by StatsService.buy) ----------------------

    @Test
    void parseIntOrDefault_parsesAValidInteger() {
        assertEquals(42, Validation.parseIntOrDefault("42", 0));
    }

    @Test
    void parseIntOrDefault_trimsSurroundingWhitespace() {
        assertEquals(9, Validation.parseIntOrDefault("  9 ", 0));
    }

    @Test
    void parseIntOrDefault_fallsBackOnGarbage() {
        assertEquals(7, Validation.parseIntOrDefault("abc", 7));
    }

    @Test
    void parseIntOrDefault_fallsBackOnEmptyString() {
        assertEquals(5, Validation.parseIntOrDefault("", 5));
    }

    @Test
    void parseIntOrDefault_fallsBackOnNull() {
        assertEquals(3, Validation.parseIntOrDefault(null, 3));
    }

    // ---- isValidUsername ---------------------------------------------------

    @Test
    void isValidUsername_acceptsLettersDigitsUnderscore() {
        assertTrue(Validation.isValidUsername("bob_1"));
    }

    @Test
    void isValidUsername_acceptsExactly50Chars() {
        assertTrue(Validation.isValidUsername(repeat("a", 50)));
    }

    @Test
    void isValidUsername_rejects51Chars() {
        assertFalse(Validation.isValidUsername(repeat("a", 51)));
    }

    @Test
    void isValidUsername_rejectsEmpty() {
        assertFalse(Validation.isValidUsername(""));
    }

    @Test
    void isValidUsername_rejectsNull() {
        assertFalse(Validation.isValidUsername(null));
    }

    @Test
    void isValidUsername_rejectsSpaces() {
        assertFalse(Validation.isValidUsername("has space"));
    }

    // ---- isValidPassword ---------------------------------------------------

    @Test
    void isValidPassword_acceptsAtMinimumLength() {
        assertTrue(Validation.isValidPassword(repeat("x", Validation.MIN_PASSWORD_LENGTH)));
    }

    @Test
    void isValidPassword_rejectsBelowMinimumLength() {
        assertFalse(Validation.isValidPassword(repeat("x", Validation.MIN_PASSWORD_LENGTH - 1)));
    }

    @Test
    void isValidPassword_rejectsNull() {
        assertFalse(Validation.isValidPassword(null));
    }

    /** Java 8 has no String.repeat; small local helper. */
    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
