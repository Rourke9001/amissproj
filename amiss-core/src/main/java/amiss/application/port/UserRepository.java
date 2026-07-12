package amiss.application.port;

import java.util.Optional;

/**
 * Port for the {@code tbluser} table (the player's credentials).
 *
 * <p>This is an <em>application-layer port</em>: the game rules depend on this interface,
 * never on a concrete database. Post-KAN-54 (V6), {@code tbluser} holds only the account's
 * name and password hash — per-save game state lives in {@code tblsave} behind {@code
 * SaveRepository} instead. Methods throw the unchecked {@link PersistenceFailureException}
 * for the caller to handle, so no persistence-technology type leaks into this interface.
 */
public interface UserRepository {

    /** The stored BCrypt hash for {@code name}, or empty if the user does not exist. */
    Optional<String> findPasswordHash(String name);

    void updatePassword(String name, String passwordHash);

    /** Inserts a brand-new player with just the given credentials. */
    void insertNewUser(String name, String passwordHash);
}
