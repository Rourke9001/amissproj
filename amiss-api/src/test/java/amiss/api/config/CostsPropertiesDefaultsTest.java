package amiss.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import amiss.application.config.ActionCosts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** With no {@code amiss.costs.*} properties at all, the bean is exactly the default table. */
@SpringBootTest(properties = "spring.flyway.enabled=false")
class CostsPropertiesDefaultsTest {

    @Autowired
    private ActionCosts costs;

    @Test
    void beanEqualsTheBuiltInDefaultsWhenNothingIsConfigured() {
        assertEquals(ActionCosts.defaults(), costs);
    }
}
