package amiss.application.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the built-in cost table (the KAN-29 approved design values, all in minutes).
 * Deployments override these via configuration; the defaults are the game's balance.
 */
class ActionCostsTest {

    @Test
    void defaults_matchTheApprovedCostTable() {
        ActionCosts d = ActionCosts.defaults();
        assertEquals(360, d.workMinutes());            // 6h
        assertEquals(360, d.studyMinutes());           // 6h
        assertEquals(360, d.relaxMinutes());           // 6h
        assertEquals(240, d.applyJobMinutes());        // 4h
        assertEquals(120, d.payRentMinutes());         // 2h
        assertEquals(0, d.eatMinutes());               // purchases cost no time (reference)
        assertEquals(0, d.shopMinutes());
        assertEquals(40, d.travelPerStepMinutes());    // cross-town (6 steps) = 4h
        assertEquals(120, d.enterBuildingMinutes());   // 2h
        assertEquals(3600, d.baseWeekMinutes());       // 60h
        assertEquals(4320, d.fedWeekMinutes());        // 72h
    }
}
