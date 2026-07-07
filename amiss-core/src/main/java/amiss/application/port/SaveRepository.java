package amiss.application.port;

import amiss.domain.model.SaveState;
import java.util.Optional;

/**
 * Persistence port for one saved game's state (KAN-53). Services load a
 * {@link SaveState}, mutate it and hand it back wholesale via {@link #update}.
 * Save CRUD (list/create/delete) is an API concern layered on top (KAN-54).
 */
public interface SaveRepository {

    Optional<SaveState> find(long saveId);

    void update(SaveState state);
}
