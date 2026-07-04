package amiss.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import amiss.application.config.ActionCosts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The KAN-29 costs-from-config acceptance: an {@code amiss.costs.*} property override is
 * visible in the {@link ActionCosts} bean, and every unset key keeps its built-in default.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "amiss.costs.work-minutes=300"
})
class CostsPropertiesBindingTest {

    @Autowired
    private ActionCosts costs;

    @Test
    void propertyOverrideIsVisibleInTheBean() {
        assertEquals(300, costs.workMinutes());
    }

    @Test
    void unsetKeysKeepTheDefaults() {
        ActionCosts d = ActionCosts.defaults();
        assertEquals(d.studyMinutes(), costs.studyMinutes());
        assertEquals(d.travelPerStepMinutes(), costs.travelPerStepMinutes());
        assertEquals(d.enterBuildingMinutes(), costs.enterBuildingMinutes());
        assertEquals(d.baseWeekMinutes(), costs.baseWeekMinutes());
        assertEquals(d.fedWeekMinutes(), costs.fedWeekMinutes());
    }
}
