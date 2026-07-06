package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import amiss.application.port.PersistenceFailureException;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for {@link JpaHelpRepository} over a mocked {@link HelpJpaRepository} — no
 * database. {@code topic} is the primary key, so the lookup is the inherited {@code
 * findById}; covers the empty-{@link Optional} case and exception translation (KAN-34).
 */
@ExtendWith(MockitoExtension.class)
class JpaHelpRepositoryTest {

    @Mock
    private HelpJpaRepository help;

    private JpaHelpRepository adapter() {
        return new JpaHelpRepository(help);
    }

    @Test
    void findDescription_returnsEmpty_whenTopicUnknown() {
        when(help.findById("ghost")).thenReturn(Optional.empty());
        assertTrue(adapter().findDescription("ghost").isEmpty());
    }

    @Test
    void findDescription_returnsDescription_whenTopicKnown() {
        HelpEntity entity = new HelpEntity();
        entity.setTopic("bank");
        entity.setDescription("Deposit or withdraw savings here.");
        when(help.findById("bank")).thenReturn(Optional.of(entity));

        assertEquals(Optional.of("Deposit or withdraw savings here."), adapter().findDescription("bank"));
    }

    @Test
    void findDescription_translatesDataAccessException() {
        when(help.findById(anyString())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(PersistenceFailureException.class, () -> adapter().findDescription("bank"));
    }

    @Test
    void findDescription_translatesPersistenceExceptionThatEscapesUnwrapped() {
        when(help.findById(anyString())).thenThrow(new PersistenceException("boom"));

        assertThrows(PersistenceFailureException.class, () -> adapter().findDescription("bank"));
    }
}
