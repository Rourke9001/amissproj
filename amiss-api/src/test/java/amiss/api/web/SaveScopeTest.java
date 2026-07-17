package amiss.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import amiss.api.error.SaveNotFoundException;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/** The KAN-54 save-ownership guard: unknown save → 404, someone else's save → 403. */
class SaveScopeTest {

    private final SaveRepository saves = mock(SaveRepository.class);
    private final SaveScope scope = new SaveScope(saves);

    private static SaveState save(long id, String owner) {
        return new SaveState(id, owner, "My Save", 0, 2, 4320, 1, 100, 0, 0, 1, 0, 1,
                null, 0, 10, 20, null, 0, 50, 50, 50, 50, false, (byte) 0, (short) 0, null,
                false, Set.of());
    }

    private static Authentication authenticationFor(String name) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(name);
        return authentication;
    }

    @Test
    void require_returnsTheSaveWhenTheCallerOwnsIt() {
        when(saves.find(42L)).thenReturn(Optional.of(save(42L, "alice")));

        SaveState resolved = scope.require(42L, authenticationFor("alice"));

        assertEquals(42L, resolved.id());
        assertEquals("alice", resolved.owner());
    }

    @Test
    void require_unknownSaveIsNotFound() {
        when(saves.find(99L)).thenReturn(Optional.empty());

        assertThrows(SaveNotFoundException.class, () -> scope.require(99L, authenticationFor("alice")));
    }

    @Test
    void require_anotherPlayersSaveIsForbidden() {
        when(saves.find(42L)).thenReturn(Optional.of(save(42L, "alice")));

        assertThrows(AccessDeniedException.class, () -> scope.require(42L, authenticationFor("bob")));
    }
}
