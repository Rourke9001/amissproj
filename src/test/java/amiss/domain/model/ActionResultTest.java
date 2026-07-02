package amiss.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the immutable {@link ActionResult} value object. No mocks/DB needed.
 */
class ActionResultTest {

    @Test
    void constructor_exposesAllThreeValues() {
        ActionResult r = new ActionResult("msg", "12:00", "100");
        assertEquals("msg", r.message());
        assertEquals("12:00", r.timer());
        assertEquals("100", r.money());
    }

    @Test
    void messageFactory_leavesTimerAndMoneyNull() {
        ActionResult r = ActionResult.message("just text");
        assertEquals("just text", r.message());
        assertNull(r.timer(), "timer should be null so the screen leaves that label unchanged");
        assertNull(r.money(), "money should be null so the screen leaves that label unchanged");
    }
}
