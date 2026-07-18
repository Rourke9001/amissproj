package amiss.api.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import amiss.api.config.CostsConfig;
import amiss.application.port.DegreeCatalog;
import amiss.application.port.JobCatalog;
import amiss.application.port.SaveDegrees;
import amiss.application.port.SaveRepository;
import amiss.application.port.Turndowns;
import amiss.domain.model.ApplianceItem;
import amiss.domain.model.DegreeSpec;
import amiss.domain.model.JobSpec;
import amiss.domain.model.SaveState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test for the five KAN-53 save-scoped port adapters (KAN-54): {@link
 * JpaSaveRepository}, {@link JpaJobCatalog}, {@link JpaDegreeCatalog}, {@link
 * JpaSaveDegrees}, {@link JpaTurndowns}, against a real, freshly-migrated MySQL container.
 * {@link CostsConfig} is imported alongside {@code PersistenceConfig} (brought in by {@link
 * MySqlITSupport}) because {@code PersistenceConfig}'s {@code SaveGameServices} bean needs
 * an {@code ActionCosts} bean to satisfy its dependencies at context startup, exactly as in
 * production. See {@link MySqlITSupport} for the container/transaction wiring.
 */
@Import(CostsConfig.class)
class SavePortsAdapterIT extends MySqlITSupport {

    @Autowired
    private SaveRepository saveRepository;

    @Autowired
    private JobCatalog jobCatalog;

    @Autowired
    private DegreeCatalog degreeCatalog;

    @Autowired
    private SaveDegrees saveDegrees;

    @Autowired
    private Turndowns turndowns;

    @Autowired
    private SaveJpaRepository saves;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        // NOT_SUPPORTED propagation = no rollback; delete everything this class seeds.
        jdbc.update("DELETE FROM tblsave_turndowns");
        jdbc.update("DELETE FROM tblsave_degrees");
        jdbc.update("DELETE FROM tblsave");
        jdbc.update("DELETE FROM tbluser WHERE name LIKE 'it_ports_%'");
    }

    // ===== SaveRepository =====

    @Test
    void findThenUpdateThenFindRoundTripsEveryMutableField() {
        long saveId = insertSave("it_ports_alice", 40, 50, 60, 70);

        SaveState loaded = saveRepository.find(saveId).orElseThrow();
        assertThat(loaded.owner()).isEqualTo("it_ports_alice");
        assertThat(loaded.jobId()).isNull();
        assertThat(loaded.currentCourseId()).isNull();
        assertThat(loaded.won()).isFalse();
        assertThat(loaded.goalWealth()).isEqualTo(40);
        assertThat(loaded.goalCareer()).isEqualTo(70);

        loaded.setLabel("Renamed");
        loaded.setPos(3, 4);
        loaded.setTimeMinutes(1000);
        loaded.setRound(2);
        loaded.setCash(999);
        loaded.setBank(111);
        loaded.setDebt(222);
        loaded.setRent(0);
        loaded.setEat(5);
        loaded.setCasualWeeks(3);
        loaded.setDressWeeks(7);
        loaded.setBusinessWeeks(0);
        loaded.setJobId(4);
        loaded.addHappiness(10);
        loaded.setExperience(30);
        loaded.setDependability(40);
        loaded.setCurrentCourseId(1);
        loaded.setEduprog(3);
        loaded.setWon(true);
        loaded.setEconomyIndex((byte) 2);
        loaded.setEconomyReading((short) 45);
        loaded.setWage(12);
        loaded.setAteFastFoodLastTurn(true);
        loaded.grantAppliance(ApplianceItem.FRIDGE);
        loaded.setRelaxation(27);
        loaded.setRelaxedThisTurn(true);

        saveRepository.update(loaded);

        SaveState reloaded = saveRepository.find(saveId).orElseThrow();
        assertThat(reloaded.label()).isEqualTo("Renamed");
        assertThat(reloaded.xpos()).isEqualTo(3);
        assertThat(reloaded.ypos()).isEqualTo(4);
        assertThat(reloaded.timeMinutes()).isEqualTo(1000);
        assertThat(reloaded.round()).isEqualTo(2);
        assertThat(reloaded.cash()).isEqualTo(999);
        assertThat(reloaded.bank()).isEqualTo(111);
        assertThat(reloaded.debt()).isEqualTo(222);
        assertThat(reloaded.rent()).isEqualTo(0);
        assertThat(reloaded.eat()).isEqualTo(5);
        assertThat(reloaded.casualWeeks()).isEqualTo(3);
        assertThat(reloaded.dressWeeks()).isEqualTo(7);
        assertThat(reloaded.businessWeeks()).isEqualTo(0);
        assertThat(reloaded.jobId()).isEqualTo(4);
        assertThat(reloaded.happiness()).isGreaterThan(0);
        assertThat(reloaded.experience()).isEqualTo(30);
        assertThat(reloaded.dependability()).isEqualTo(40);
        assertThat(reloaded.currentCourseId()).isEqualTo(1);
        assertThat(reloaded.eduprog()).isEqualTo(3);
        assertThat(reloaded.won()).isTrue();
        assertThat(reloaded.economyIndex()).isEqualTo((byte) 2);
        assertThat(reloaded.economyReading()).isEqualTo((short) 45);
        assertThat(reloaded.wage()).isEqualTo(Integer.valueOf(12));
        assertThat(reloaded.ateFastFoodLastTurn()).isTrue();
        assertThat(reloaded.ownedAppliances()).isEqualTo(Set.of(ApplianceItem.FRIDGE));
        assertThat(reloaded.relaxation()).isEqualTo(27);
        assertThat(reloaded.relaxedThisTurn()).isTrue();
        // goals are immutable on SaveState and untouched by update()
        assertThat(reloaded.goalWealth()).isEqualTo(40);

        // flip jobId/currentCourseId back to null and won back to false
        reloaded.setJobId(null);
        reloaded.setCurrentCourseId(null);
        reloaded.setWon(false);
        reloaded.setAteFastFoodLastTurn(false);
        reloaded.grantAppliance(ApplianceItem.FREEZER);
        saveRepository.update(reloaded);

        SaveState finalState = saveRepository.find(saveId).orElseThrow();
        assertThat(finalState.jobId()).isNull();
        assertThat(finalState.currentCourseId()).isNull();
        assertThat(finalState.won()).isFalse();
        assertThat(finalState.ateFastFoodLastTurn()).isFalse();
        // update() replaces the join-table rows rather than accumulating them: the second
        // update must leave exactly both appliances, not a duplicate Fridge row.
        assertThat(finalState.ownedAppliances())
                .isEqualTo(Set.of(ApplianceItem.FRIDGE, ApplianceItem.FREEZER));
    }

    @Test
    void findReturnsEmptyForAMissingSave() {
        assertThat(saveRepository.find(-1L)).isEmpty();
    }

    // ===== JobCatalog =====

    @Test
    void jobCatalogByIdAndByLocationAndAllMatchTheSeededCatalog() {
        Optional<JobSpec> cook = jobCatalog.byId(4);
        assertThat(cook).isPresent();
        assertThat(cook.get().name()).isEqualTo("Cook");
        assertThat(cook.get().location()).isEqualTo("Monolith Burgers");
        assertThat(cook.get().alwaysHired()).isTrue();

        assertThat(jobCatalog.byId(-1)).isEmpty();

        List<JobSpec> factory = jobCatalog.byLocation("Factory");
        assertThat(factory).hasSize(9);
        assertThat(factory.get(0).name()).isEqualTo("Janitor");

        assertThat(jobCatalog.all()).hasSize(39);
    }

    @Test
    void requiredDegreesMatchesTheSeededSpotChecks() {
        assertThat(jobCatalog.requiredDegrees(32)).containsExactlyInAnyOrder(3, 4); // Broker
        assertThat(jobCatalog.requiredDegrees(4)).isEmpty(); // Cook
    }

    // ===== DegreeCatalog =====

    @Test
    void degreeCatalogHasElevenRowsWithPrereqSpotCheck() {
        List<DegreeSpec> all = degreeCatalog.all();
        assertThat(all).hasSize(11);

        DegreeSpec juniorCollege = byName(all, "Junior College");
        assertThat(juniorCollege.prereqDegreeId()).isNull();

        DegreeSpec businessAdmin = byName(all, "Business Administration");
        assertThat(businessAdmin.prereqDegreeId()).isEqualTo(juniorCollege.id());

        Optional<DegreeSpec> byId = degreeCatalog.byId(juniorCollege.id());
        assertThat(byId).isPresent();
        assertThat(byId.get().name()).isEqualTo("Junior College");

        assertThat(degreeCatalog.byId(-1)).isEmpty();
    }

    // ===== SaveDegrees =====

    @Test
    void awardIsIdempotentAndEarnedReflectsAwardedDegrees() {
        long saveId = insertSave("it_ports_carol", 10, 10, 10, 10);

        assertThat(saveDegrees.earned(saveId)).isEmpty();

        saveDegrees.award(saveId, 1);
        saveDegrees.award(saveId, 1); // double-award must not blow up
        saveDegrees.award(saveId, 2);

        assertThat(saveDegrees.earned(saveId)).containsExactlyInAnyOrder(1, 2);
    }

    // ===== Turndowns =====

    @Test
    void recordThenIsTurnedDownAndReRecordUpdatesRound() {
        long saveId = insertSave("it_ports_dave", 10, 10, 10, 10);
        int jobId = 32;

        assertThat(turndowns.isTurnedDown(saveId, jobId, 1)).isFalse();

        turndowns.record(saveId, jobId, 1);

        assertThat(turndowns.isTurnedDown(saveId, jobId, 1)).isTrue();
        assertThat(turndowns.isTurnedDown(saveId, jobId, 2)).isFalse();

        turndowns.record(saveId, jobId, 2);

        assertThat(turndowns.isTurnedDown(saveId, jobId, 1)).isFalse();
        assertThat(turndowns.isTurnedDown(saveId, jobId, 2)).isTrue();
    }

    // ===== helpers =====

    private static DegreeSpec byName(List<DegreeSpec> all, String name) {
        return all.stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
    }

    /** Inserts an owning user (FK; tbluser is credentials-only post-V6, KAN-54) plus one save. */
    private long insertSave(String owner, int goalWealth, int goalHappiness, int goalEducation, int goalCareer) {
        jdbc.update("INSERT INTO tbluser (name, password) VALUES (?, 'x')", owner);
        SaveEntity entity = saves.saveAndFlush(
                new SaveEntity(owner, "Save 1", goalWealth, goalHappiness, goalEducation, goalCareer, 3600));
        return entity.getId();
    }
}
