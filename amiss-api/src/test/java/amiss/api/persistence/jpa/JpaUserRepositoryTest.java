package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
 * Unit tests for {@link JpaUserRepository} over a mocked {@link UserJpaRepository} — no
 * database. Covers the credentials-only port contract (KAN-54): find/update the password
 * hash, insert a brand-new row with nothing but the given credentials, and exception
 * translation.
 */
@ExtendWith(MockitoExtension.class)
class JpaUserRepositoryTest {

    @Mock
    private UserJpaRepository users;

    private JpaUserRepository adapter() {
        return new JpaUserRepository(users);
    }

    // ---- reads -------------------------------------------------------------

    @Test
    void findPasswordHash_returnsEmpty_whenMissing() {
        when(users.findPasswordHash("ghost")).thenReturn(Optional.empty());
        assertTrue(adapter().findPasswordHash("ghost").isEmpty());
    }

    @Test
    void findPasswordHash_returnsHash_whenPresent() {
        when(users.findPasswordHash("bob")).thenReturn(Optional.of("bcrypt-hash"));
        assertEquals(Optional.of("bcrypt-hash"), adapter().findPasswordHash("bob"));
    }

    // ---- insertNewUser: credentials only -----------------------------------

    @Test
    void insertNewUser_savesAnEntityWithNothingButNameAndPassword() {
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);

        adapter().insertNewUser("newplayer", "bcrypt-hash");

        verify(users).saveAndFlush(captor.capture());
        UserEntity saved = captor.getValue();
        assertEquals("newplayer", saved.getName());
        assertEquals("bcrypt-hash", saved.getPassword());
    }

    // ---- exception translation ---------------------------------------------------

    @Test
    void read_translatesDataAccessException() {
        when(users.findPasswordHash(anyString())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(PersistenceFailureException.class, () -> adapter().findPasswordHash("bob"));
    }

    @Test
    void write_translatesDataAccessException() {
        doThrow(new DataAccessResourceFailureException("db down")).when(users).updatePassword(anyString(), anyString());

        assertThrows(PersistenceFailureException.class, () -> adapter().updatePassword("bob", "new-hash"));
    }

    @Test
    void write_translatesPersistenceExceptionThatEscapesUnwrapped() {
        when(users.saveAndFlush(any(UserEntity.class))).thenThrow(new PersistenceException("boom"));

        assertThrows(PersistenceFailureException.class, () -> adapter().insertNewUser("bob", "hash"));
    }
}
