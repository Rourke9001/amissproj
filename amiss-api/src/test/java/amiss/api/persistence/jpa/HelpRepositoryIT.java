package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import amiss.application.port.HelpRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for {@link JpaHelpRepository} (the {@link HelpRepository} port,
 * {@code tblhelp}) against a real, freshly-migrated MySQL container (KAN-35). {@code tblhelp}
 * is reference data owned by {@code V2__seed_reference_data.sql} — read-only, so no
 * {@code @AfterEach} cleanup is needed here. See {@link MySqlITSupport} for the
 * container/transaction wiring.
 */
class HelpRepositoryIT extends MySqlITSupport {

    @Autowired
    private HelpRepository help;

    @ParameterizedTest
    @ValueSource(strings = {"Controls", "How to Win", "Tips", "Getting A Job"})
    void findDescription_resolvesEverySeededTopic_toANonEmptyDescription(String topic) {
        Optional<String> description = help.findDescription(topic);

        assertTrue(description.isPresent(), () -> "expected a description for topic '" + topic + "'");
        assertFalse(description.get().isBlank());
    }

    @Test
    void findDescription_isEmpty_forAnUnknownTopic() {
        assertFalse(help.findDescription("Not A Real Topic").isPresent());
    }
}
