package amiss.api.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.port.PersistenceFailureException;
import amiss.domain.model.User;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for {@link JpaUserRepository} over a mocked {@link UserJpaRepository} — no
 * database. Covers the missing-row fallbacks the JDBC adapter always used, the
 * deposit/withdraw affected-row semantics, the exact {@code insertNewUser} defaults, and
 * exception translation (KAN-34).
 */
@ExtendWith(MockitoExtension.class)
class JpaUserRepositoryTest {

    @Mock
    private UserJpaRepository users;

    private JpaUserRepository adapter() {
        return new JpaUserRepository(users);
    }

    // ---- missing-row fallbacks (mirrors JdbcUserRepository defaults) ----------

    @Test
    void getXpos_returnsZero_whenMissing() {
        when(users.findXpos("bob")).thenReturn(Optional.empty());
        assertEquals(0, adapter().getXpos("bob"));
    }

    @Test
    void getXpos_returnsStoredValue_whenPresent() {
        when(users.findXpos("bob")).thenReturn(Optional.of(7));
        assertEquals(7, adapter().getXpos("bob"));
    }

    @Test
    void getYpos_returnsZero_whenMissing() {
        when(users.findYpos("bob")).thenReturn(Optional.empty());
        assertEquals(0, adapter().getYpos("bob"));
    }

    @Test
    void getTime_returnsMinusOne_whenMissing() {
        when(users.findTime("bob")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getTime("bob"));
    }

    @Test
    void getRound_returnsMinusOne_whenMissing() {
        when(users.findRound("bob")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getRound("bob"));
    }

    @Test
    void getCash_returnsMinusOne_whenMissing() {
        when(users.findCash("bob")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getCash("bob"));
    }

    @Test
    void getRent_returnsMinusOne_whenMissing() {
        when(users.findRent("bob")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getRent("bob"));
    }

    @Test
    void getDebt_returnsMinusOne_whenMissing() {
        when(users.findDebt("bob")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getDebt("bob"));
    }

    @Test
    void getEat_returnsZero_whenMissing() {
        when(users.findEat("bob")).thenReturn(Optional.empty());
        assertEquals(0, adapter().getEat("bob"));
    }

    @Test
    void getJob_returnsNull_whenMissing() {
        when(users.findJob("bob")).thenReturn(Optional.empty());
        assertNull(adapter().getJob("bob"));
    }

    @Test
    void getJob_returnsStoredValue_whenPresent() {
        when(users.findJob("bob")).thenReturn(Optional.of("Chef"));
        assertEquals("Chef", adapter().getJob("bob"));
    }

    @Test
    void getUserClothing_returnsNull_whenMissing() {
        when(users.findClothing("bob")).thenReturn(Optional.empty());
        assertNull(adapter().getUserClothing("bob"));
    }

    @Test
    void getUserClothing_returnsStringifiedInt_whenPresent() {
        when(users.findClothing("bob")).thenReturn(Optional.of(3));
        assertEquals("3", adapter().getUserClothing("bob"));
    }

    @Test
    void getBank_returnsMinusOne_whenMissing() {
        when(users.findBank("bob")).thenReturn(Optional.empty());
        assertEquals(-1, adapter().getBank("bob"));
    }

    // ---- Optional-returning lookups --------------------------------------------

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

    @Test
    void findByName_returnsEmpty_whenMissing() {
        when(users.findById("ghost")).thenReturn(Optional.empty());
        assertTrue(adapter().findByName("ghost").isEmpty());
    }

    @Test
    void findByName_mapsEveryEntityFieldToDomainUser_whenPresent() {
        UserEntity entity = new UserEntity();
        entity.setName("bob");
        entity.setPassword("bcrypt-hash");
        entity.setXpos(1);
        entity.setYpos(2);
        entity.setTime(4000);
        entity.setCash(150);
        entity.setRound(2);
        entity.setJob("Chef");
        entity.setClothing(2);
        entity.setRent(0);
        entity.setEat(1);
        entity.setDebt(50);
        entity.setBank(75);
        when(users.findById("bob")).thenReturn(Optional.of(entity));

        User user = adapter().findByName("bob").orElseThrow();

        assertEquals("bob", user.getUser());
        assertEquals(1, user.getXpos());
        assertEquals(2, user.getYpos());
        assertEquals(4000, user.getTime());
        assertEquals(150, user.getCash());
        assertEquals(2, user.getRound());
        assertEquals("Chef", user.getJob());
        assertEquals(2, user.getClothing());
        assertEquals(0, user.getRent());
        assertEquals(1, user.getEat());
        assertEquals(50, user.getDebt());
        // User (the domain object) has no bank field — matches JdbcUserRepository#findByName.
    }

    @Test
    void highScores_mapsRowsToStringPairsPreservingOrder() {
        when(users.highScores()).thenReturn(List.of(
                new Object[]{"alice", 5},
                new Object[]{"bob", 3}));

        List<String[]> scores = adapter().highScores();

        assertEquals(2, scores.size());
        assertEquals("alice", scores.get(0)[0]);
        assertEquals("5", scores.get(0)[1]);
        assertEquals("bob", scores.get(1)[0]);
        assertEquals("3", scores.get(1)[1]);
    }

    // ---- bank: affected-row semantics -------------------------------------------

    @Test
    void depositToBank_returnsTrue_whenOneRowAffected() {
        when(users.depositToBank("bob", 50)).thenReturn(1);
        assertTrue(adapter().depositToBank("bob", 50));
    }

    @Test
    void depositToBank_returnsFalse_whenZeroRowsAffected() {
        when(users.depositToBank("bob", 5000)).thenReturn(0);
        assertFalse(adapter().depositToBank("bob", 5000));
    }

    @Test
    void withdrawFromBank_returnsTrue_whenOneRowAffected() {
        when(users.withdrawFromBank("bob", 20)).thenReturn(1);
        assertTrue(adapter().withdrawFromBank("bob", 20));
    }

    @Test
    void withdrawFromBank_returnsFalse_whenZeroRowsAffected() {
        when(users.withdrawFromBank("bob", 5000)).thenReturn(0);
        assertFalse(adapter().withdrawFromBank("bob", 5000));
    }

    // ---- insertNewUser: exact game defaults --------------------------------------

    @Test
    void insertNewUser_savesEntityWithExactGameDefaults() {
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);

        adapter().insertNewUser("newplayer", "bcrypt-hash");

        verify(users).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertEquals("newplayer", saved.getName());
        assertEquals("bcrypt-hash", saved.getPassword());
        assertEquals(0, saved.getXpos());
        assertEquals(2, saved.getYpos());
        assertEquals(4320, saved.getTime());
        assertEquals(100, saved.getCash());
        assertEquals(1, saved.getRound());
        assertEquals("Unemployed", saved.getJob());
        assertEquals(1, saved.getClothing());
        assertEquals(1, saved.getRent());
        assertEquals(0, saved.getEat());
        assertEquals(0, saved.getDebt());
        assertEquals(0, saved.getBank());
    }

    // ---- exception translation ---------------------------------------------------

    @Test
    void read_translatesDataAccessException() {
        when(users.findXpos(anyString())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(PersistenceFailureException.class, () -> adapter().getXpos("bob"));
    }

    @Test
    void write_translatesDataAccessException() {
        doThrow(new DataAccessResourceFailureException("db down")).when(users).updateCash(anyString(), anyInt());

        assertThrows(PersistenceFailureException.class, () -> adapter().updateCash("bob", 10));
    }

    @Test
    void write_translatesPersistenceExceptionThatEscapesUnwrapped() {
        when(users.depositToBank(anyString(), anyInt())).thenThrow(new PersistenceException("boom"));

        assertThrows(PersistenceFailureException.class, () -> adapter().depositToBank("bob", 10));
    }
}
