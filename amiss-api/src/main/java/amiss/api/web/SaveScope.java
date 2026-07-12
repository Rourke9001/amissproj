package amiss.api.web;

import amiss.api.error.SaveNotFoundException;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The save-ownership guard (KAN-54): every save-scoped controller method resolves its
 * {@code {saveId}} through here first, instead of loading it directly off {@link
 * SaveRepository}. An unknown id is a 404; someone else's save throws the same {@link
 * AccessDeniedException} type {@code PlayerScopeFilter} used to throw, so it still leaves
 * as the {@code urn:amiss:forbidden} envelope via {@code SecurityConfig}'s
 * {@code AccessDeniedHandler} even though nothing here is a servlet filter.
 *
 * <p>A plain {@code @Component} rather than a filter: the save id sits inside a path
 * template ({@code /api/saves/{saveId}/...}) that varies per controller, unlike the flat
 * {@code /api/players/{username}} segment a single regex could match everywhere.
 */
@Component
public class SaveScope {

    private final SaveRepository saves;

    public SaveScope(SaveRepository saves) {
        this.saves = saves;
    }

    /**
     * @return the save, already confirmed to belong to {@code authentication}
     * @throws SaveNotFoundException if no save has this id at all (404)
     * @throws AccessDeniedException if the save belongs to a different account (403)
     */
    public SaveState require(long saveId, Authentication authentication) {
        SaveState save = saves.find(saveId).orElseThrow(() -> new SaveNotFoundException(saveId));
        if (!save.owner().equals(authentication.getName())) {
            throw new AccessDeniedException("You may only access your own save");
        }
        return save;
    }
}
