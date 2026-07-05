package amiss.api.persistence.jpa;

import amiss.application.port.HelpRepository;
import amiss.application.port.PersistenceFailureException;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;

/**
 * JPA adapter for {@link HelpRepository} ({@code tblhelp}), delegating to
 * {@link HelpJpaRepository} (KAN-34). {@code topic} is the entity id, so the lookup is
 * the inherited {@code findById}, matching the core's {@code
 * JdbcHelpRepository#findDescription} 1:1.
 *
 * <p>Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig}, matching the JDBC adapter's wiring style.
 */
public class JpaHelpRepository implements HelpRepository {

    private final HelpJpaRepository help;

    public JpaHelpRepository(HelpJpaRepository help) {
        this.help = help;
    }

    @Override
    public Optional<String> findDescription(String topic) {
        return translate("find description", () -> help.findById(topic).map(HelpEntity::getDescription));
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tblhelp)", e);
        }
    }
}
