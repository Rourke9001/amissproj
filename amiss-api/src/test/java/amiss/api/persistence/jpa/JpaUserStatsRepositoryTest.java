package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import amiss.application.port.PersistenceFailureException;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for {@link JpaUserStatsRepository} over mocked {@link UserStatsJpaRepository}
 * / {@link UserJpaRepository} — no database. Covers the missing-row fallbacks the JDBC
 * adapter always used, the {@code insertNewStats} MapsId wiring (no extra SELECT), and
 * exception translation (KAN-34).
 */
@ExtendWith(MockitoExtension.class)
class JpaUserStatsRepositoryTest {

    @Mock
    private UserStatsJpaRepository stats;
    @Mock
    private UserJpaRepository users;

    private JpaUserStatsRepository adapter() {
        return new JpaUserStatsRepository(stats, users);
    }

    @Test
    void getEducation_returnsMinusOne_whenMissing() {
        when(stats.findEducation("bob")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getEducation("bob"));
    }

    @Test
    void getEducation_returnsStoredValue_whenPresent() {
        when(stats.findEducation("bob")).thenReturn(Optional.of(4));
        assertEquals(4, adapter().getEducation("bob"));
    }

    @Test
    void getEduprog_returnsMinusOne_whenMissing() {
        when(stats.findEduprog("bob")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getEduprog("bob"));
    }

    @Test
    void getWork_returnsNull_whenMissing() {
        when(stats.findWork("bob")).thenReturn(Optional.empty());
        assertNull(adapter().getWork("bob"));
    }

    @Test
    void getWork_returnsStringifiedInt_whenPresent() {
        when(stats.findWork("bob")).thenReturn(Optional.of(12));
        assertEquals("12", adapter().getWork("bob"));
    }

    @Test
    void getHappiness_returnsNull_whenMissing() {
        when(stats.findHappiness("bob")).thenReturn(Optional.empty());
        assertNull(adapter().getHappiness("bob"));
    }

    @Test
    void getHappiness_returnsStringifiedInt_whenPresent() {
        when(stats.findHappiness("bob")).thenReturn(Optional.of(8));
        assertEquals("8", adapter().getHappiness("bob"));
    }

    @Test
    void insertNewStats_setsAllZeroStatsAndMapsIdViaGetReferenceById_noExtraSelect() {
        UserEntity reference = new UserEntity();
        reference.setName("newplayer");
        when(users.getReferenceById("newplayer")).thenReturn(reference);
        ArgumentCaptor<UserStatsEntity> captor = ArgumentCaptor.forClass(UserStatsEntity.class);

        adapter().insertNewStats("newplayer");

        verify(users).getReferenceById("newplayer");
        verify(stats).save(captor.capture());
        UserStatsEntity saved = captor.getValue();
        assertSame(reference, saved.getUser());
        assertEquals(0, saved.getHappiness());
        assertEquals(0, saved.getEducation());
        assertEquals(0, saved.getWork());
        assertEquals(0, saved.getEduprog());
        // No findById/findByName on the user repository — getReferenceById is a proxy, not a SELECT.
        verifyNoMoreInteractions(users);
    }

    @Test
    void read_translatesDataAccessException() {
        when(stats.findEducation(anyString())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(PersistenceFailureException.class, () -> adapter().getEducation("bob"));
    }

    @Test
    void write_translatesDataAccessException() {
        doThrow(new DataAccessResourceFailureException("db down")).when(stats).incrementWork(anyString());

        assertThrows(PersistenceFailureException.class, () -> adapter().incrementWork("bob"));
    }

    @Test
    void write_translatesPersistenceExceptionThatEscapesUnwrapped() {
        doThrow(new PersistenceException("boom")).when(stats).resetStats(anyString());

        assertThrows(PersistenceFailureException.class, () -> adapter().resetStats("bob"));
    }
}
