package amiss.api.persistence.jpa;

import amiss.application.port.PersistenceFailureException;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link SaveRepository} ({@code tblsave}), delegating to
 * {@link SaveJpaRepository} (KAN-54). {@code update} loads the entity fresh, copies every
 * mutable {@link SaveState} field back onto it and saves — the save-scoped counterpart of
 * {@code JpaUserRepository}'s per-field updates, but wholesale since the port hands back
 * one state object rather than one field at a time.
 *
 * <p>Not a Spring stereotype bean: wired explicitly by {@code
 * amiss.api.config.PersistenceConfig}, matching the other JPA adapters' wiring style.
 */
public class JpaSaveRepository implements SaveRepository {

    private final SaveJpaRepository saves;

    public JpaSaveRepository(SaveJpaRepository saves) {
        this.saves = saves;
    }

    @Override
    public Optional<SaveState> find(long saveId) {
        return translate("find save", () -> saves.findById(saveId).map(JpaSaveRepository::toDomain));
    }

    @Override
    @Transactional
    public void update(SaveState state) {
        translateRun("update save", () -> {
            SaveEntity entity = saves.findById(state.id())
                    .orElseThrow(() -> new PersistenceFailureException(
                            "No save " + state.id() + " to update (tblsave)"));
            entity.setLabel(state.label());
            entity.setXpos(state.xpos());
            entity.setYpos(state.ypos());
            entity.setTime(state.timeMinutes());
            entity.setRound(state.round());
            entity.setCash(state.cash());
            entity.setBank(state.bank());
            entity.setDebt(state.debt());
            entity.setRent(state.rent());
            entity.setEat(state.eat());
            entity.setCasualWeeks(state.casualWeeks());
            entity.setDressWeeks(state.dressWeeks());
            entity.setBusinessWeeks(state.businessWeeks());
            entity.setJobId(state.jobId());
            entity.setWage(state.wage());
            entity.setHappiness(state.happiness());
            entity.setExperience(state.experience());
            entity.setDependability(state.dependability());
            entity.setCurrentCourseId(state.currentCourseId());
            entity.setEduprog(state.eduprog());
            entity.setWon(state.won() ? 1 : 0);
            entity.setEconomyIndex(state.economyIndex());
            entity.setEconomyReading(state.economyReading());
            entity.setAteFastFoodLastTurn(state.ateFastFoodLastTurn());
            entity.getOwnedAppliances().clear();
            entity.getOwnedAppliances().addAll(state.ownedAppliances());
            saves.saveAndFlush(entity);
        });
    }

    private static SaveState toDomain(SaveEntity e) {
        return new SaveState(e.getId(), e.getOwner(), e.getLabel(), e.getXpos(), e.getYpos(),
                e.getTime(), e.getRound(), e.getCash(), e.getBank(), e.getDebt(), e.getRent(),
                e.getEat(), e.getCasualWeeks(), e.getDressWeeks(), e.getBusinessWeeks(),
                e.getJobId(), e.getHappiness(), e.getExperience(),
                e.getDependability(), e.getCurrentCourseId(), e.getEduprog(),
                e.getGoalWealth(), e.getGoalHappiness(), e.getGoalEducation(), e.getGoalCareer(),
                e.getWon() != 0, e.getEconomyIndex(), e.getEconomyReading(), e.getWage(),
                e.isAteFastFoodLastTurn(), e.getOwnedAppliances());
    }

    /** Runs {@code call}, translating any persistence failure into {@link PersistenceFailureException}. */
    private <T> T translate(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException | PersistenceException e) {
            throw new PersistenceFailureException("Failed to " + operation + " (tblsave)", e);
        }
    }

    private void translateRun(String operation, Runnable call) {
        translate(operation, () -> {
            call.run();
            return null;
        });
    }
}
