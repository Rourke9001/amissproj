package amiss.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import amiss.application.config.ActionCosts;
import org.junit.jupiter.api.Test;

/**
 * The test classpath's application.properties carries two cost entries — a valid override
 * and a malformed one — so these tests exercise the {@link Config#actionCosts()} precedence
 * chain: properties beat defaults, malformed values fall back, unset keys keep defaults.
 */
class ConfigActionCostsTest {

    @Test
    void propertyOverrideBeatsTheDefault() {
        assertEquals(300, Config.actionCosts().workMinutes());
    }

    @Test
    void malformedOverrideFallsBackToTheDefault() {
        assertEquals(ActionCosts.defaults().eatMinutes(), Config.actionCosts().eatMinutes());
    }

    @Test
    void unsetKeysKeepTheDefaults() {
        ActionCosts d = ActionCosts.defaults();
        ActionCosts c = Config.actionCosts();
        assertEquals(d.studyMinutes(), c.studyMinutes());
        assertEquals(d.travelPerStepMinutes(), c.travelPerStepMinutes());
        assertEquals(d.enterBuildingMinutes(), c.enterBuildingMinutes());
        assertEquals(d.baseWeekMinutes(), c.baseWeekMinutes());
        assertEquals(d.fedWeekMinutes(), c.fedWeekMinutes());
    }
}
