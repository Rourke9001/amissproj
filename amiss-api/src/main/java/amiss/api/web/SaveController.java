package amiss.api.web;

import amiss.api.error.InvalidGoalException;
import amiss.api.error.SaveNotFoundException;
import amiss.api.persistence.jpa.SaveEntity;
import amiss.api.persistence.jpa.SaveJpaRepository;
import amiss.api.web.dto.CreateSaveRequest;
import amiss.api.web.dto.SaveSummaryDto;
import amiss.application.port.PersistenceFailureException;
import java.util.List;
import java.util.Random;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Save slots CRUD (KAN-54): list an account's own saves, create a new one (goals chosen or
 * rolled), and delete one. Drives {@link SaveJpaRepository} directly — plain row CRUD needs
 * no save-scoped rules service. Delete resolves ownership here (not via {@code SaveScope})
 * since it already needs a {@link SaveEntity}, not a domain {@code SaveState}, to delete.
 */
@RestController
@RequestMapping("/api/saves")
public class SaveController {

    private static final int MIN_GOAL = 10;
    private static final int MAX_GOAL = 100;

    private final SaveJpaRepository saves;
    private final Random random = new Random();

    public SaveController(SaveJpaRepository saves) {
        this.saves = saves;
    }

    @GetMapping
    public List<SaveSummaryDto> list(Authentication authentication) {
        return saves.findByOwnerOrderByUpdatedAtDesc(authentication.getName()).stream()
                .map(SaveController::toSummary)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaveSummaryDto create(Authentication authentication, @RequestBody CreateSaveRequest request) {
        int[] goals = resolveGoals(request);
        SaveEntity created = saves.save(new SaveEntity(authentication.getName(), request.label(),
                goals[0], goals[1], goals[2], goals[3]));
        // Re-fetch: created_at/updated_at are DB-maintained (DEFAULT CURRENT_TIMESTAMP), so the
        // in-memory entity from save() doesn't carry them until a fresh SELECT does.
        SaveEntity fresh = saves.findById(created.getId())
                .orElseThrow(() -> new PersistenceFailureException("Save vanished immediately after creation"));
        return toSummary(fresh);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Authentication authentication) {
        SaveEntity entity = saves.findById(id).orElseThrow(() -> new SaveNotFoundException(id));
        if (!entity.getOwner().equals(authentication.getName())) {
            throw new AccessDeniedException("You may only delete your own save");
        }
        saves.delete(entity);
    }

    private int[] resolveGoals(CreateSaveRequest request) {
        if (Boolean.TRUE.equals(request.random())) {
            return new int[]{roll(), roll(), roll(), roll()};
        }
        CreateSaveRequest.GoalTargets goals = request.goals();
        if (goals == null) {
            throw new InvalidGoalException("goals are required unless random is true");
        }
        return new int[]{
                requireInRange(goals.wealth()), requireInRange(goals.happiness()),
                requireInRange(goals.education()), requireInRange(goals.career())};
    }

    private int roll() {
        return MIN_GOAL + random.nextInt(MAX_GOAL - MIN_GOAL + 1);
    }

    private static int requireInRange(Integer value) {
        if (value == null || value < MIN_GOAL || value > MAX_GOAL) {
            throw new InvalidGoalException("Each goal must be between " + MIN_GOAL + " and " + MAX_GOAL);
        }
        return value;
    }

    private static SaveSummaryDto toSummary(SaveEntity e) {
        return new SaveSummaryDto(e.getId(), e.getLabel(), e.getRound(), e.getCash(), e.getWon() != 0, e.getUpdatedAt());
    }
}
