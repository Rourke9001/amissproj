package amiss.api.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import amiss.api.config.CostsConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * KAN-52: proves the V5 expand migration on a real, freshly-migrated MySQL 9 —
 * the wiki catalog seed (11 degrees / 39 jobs / degree requirements) and the
 * new save tables' entity round-trips.
 *
 * <p>The user→save copy rule itself (run once, at migration time, against
 * whatever accounts already exist) is exercised end-to-end by {@link
 * LegacyToSaveMigrationIT} (KAN-54), which seeds a genuine pre-V5 account and
 * runs the real V5/V6 migrations against it. This class only needs an owner
 * row to satisfy {@code tblsave}'s FK, so {@link #insertUser} writes the
 * post-V6 credentials-only shape rather than the legacy one.
 *
 * <p>{@link CostsConfig} is imported for the same reason as in {@link
 * SavePortsAdapterIT}: {@code PersistenceConfig}'s {@code SaveGameServices}
 * bean needs an {@code ActionCosts} bean to satisfy its dependencies at
 * context startup, exactly as in production.
 */
@Import(CostsConfig.class)
class SaveSchemaIT extends MySqlITSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DegreeJpaRepository degrees;

    @Autowired
    private JobCatalogJpaRepository jobs;

    @Autowired
    private SaveJpaRepository saves;

    @Autowired
    private SaveDegreeJpaRepository saveDegrees;

    @Autowired
    private SaveTurndownJpaRepository turndowns;

    @AfterEach
    void cleanUp() {
        // NOT_SUPPORTED propagation = no rollback; delete everything this class seeds.
        jdbc.update("DELETE FROM tblsave_turndowns");
        jdbc.update("DELETE FROM tblsave_degrees");
        jdbc.update("DELETE FROM tblsave");
        jdbc.update("DELETE FROM tbluser WHERE name LIKE 'it_save_%'");
    }

    // ===== catalog seed =====

    @Test
    void seedsTheElevenDegreeCatalogWithPrerequisites() {
        List<DegreeEntity> all = degrees.findAll();
        assertThat(all).hasSize(11);

        assertThat(byName(all, "Junior College").getPrereqDegreeId()).isNull();
        assertThat(byName(all, "Trade School").getPrereqDegreeId()).isNull();
        assertThat(byName(all, "Business Administration").getPrereqDegreeId())
                .isEqualTo(byName(all, "Junior College").getId());
        assertThat(byName(all, "Engineering").getPrereqDegreeId())
                .isEqualTo(byName(all, "Pre-Engineering").getId());
        assertThat(byName(all, "Publishing").getPrereqDegreeId())
                .isEqualTo(byName(all, "Research").getId());
    }

    @Test
    void seedsTheThirtyNineJobCatalog() {
        List<JobCatalogEntity> all = jobs.findAll();
        assertThat(all).hasSize(39);

        // 9 hiring workplaces, names verbatim from domain.board.Location.
        assertThat(all).extracting(JobCatalogEntity::getLocation).containsOnly(
                "Z-Mart", "Monolith Burgers", "QT Clothing", "Socket City", "Hi-Tech U",
                "Factory", "Bank", "Black's Market", "Rent Office");

        JobCatalogEntity cook = jobs.findById(4).orElseThrow();
        assertThat(cook.getJob()).isEqualTo("Cook");
        assertThat(cook.getReqExperience()).isZero();
        assertThat(cook.getRequiredDegreeIds()).isEmpty();

        JobCatalogEntity broker = jobs.findById(32).orElseThrow();
        assertThat(broker.getJob()).isEqualTo("Broker");
        assertThat(broker.getWage()).isEqualTo(22);
        assertThat(broker.getReqExperience()).isEqualTo(70);
        assertThat(broker.getReqDependability()).isEqualTo(70);
        assertThat(broker.getReqClothing()).isEqualTo(3);
        // Business Administration (3) + Academic (4) — the two-degree top jobs.
        assertThat(broker.getRequiredDegreeIds()).containsExactlyInAnyOrder(3, 4);
    }

    @Test
    void listsAWorkplacesJobsInCatalogOrder() {
        List<JobCatalogEntity> factory = jobs.findByLocationOrderById("Factory");
        assertThat(factory).hasSize(9);
        assertThat(factory.get(0).getJob()).isEqualTo("Janitor");
        assertThat(factory.get(8).getJob()).isEqualTo("General Manager");
        assertThat(factory.get(8).getRequiredDegreeIds()).containsExactlyInAnyOrder(3, 8);
    }

    // ===== save round-trips =====

    @Test
    void persistsASaveWithNewGameDefaults() {
        insertUser("it_save_alice");

        SaveEntity created = saves.saveAndFlush(new SaveEntity("it_save_alice", "First try", 40, 50, 60, 70));
        SaveEntity reloaded = saves.findById(created.getId()).orElseThrow();

        assertThat(reloaded.getOwner()).isEqualTo("it_save_alice");
        assertThat(reloaded.getLabel()).isEqualTo("First try");
        assertThat(reloaded.getTime()).isEqualTo(4320);
        assertThat(reloaded.getCash()).isEqualTo(100);
        assertThat(reloaded.getExperience()).isEqualTo(10);
        assertThat(reloaded.getDependability()).isEqualTo(20);
        assertThat(reloaded.getJobId()).isNull();
        assertThat(reloaded.getGoalWealth()).isEqualTo(40);
        assertThat(reloaded.getGoalCareer()).isEqualTo(70);
        assertThat(reloaded.getWon()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void scopesSavesByOwner() {
        insertUser("it_save_alice");
        insertUser("it_save_bob");
        SaveEntity alices = saves.saveAndFlush(new SaveEntity("it_save_alice", "Save 1", 10, 10, 10, 10));
        saves.saveAndFlush(new SaveEntity("it_save_bob", "Save 1", 10, 10, 10, 10));

        assertThat(saves.findByOwnerOrderByUpdatedAtDesc("it_save_alice"))
                .extracting(SaveEntity::getId).containsExactly(alices.getId());
        assertThat(saves.findByIdAndOwner(alices.getId(), "it_save_bob")).isEmpty();
        assertThat(saves.findByIdAndOwner(alices.getId(), "it_save_alice")).isPresent();
    }

    @Test
    void persistsEarnedDegreesAndTurndownsAndCascadesOnSaveDelete() {
        insertUser("it_save_alice");
        SaveEntity save = saves.saveAndFlush(new SaveEntity("it_save_alice", "Save 1", 10, 10, 10, 10));

        saveDegrees.saveAndFlush(new SaveDegreeEntity(save.getId(), 1));
        saveDegrees.saveAndFlush(new SaveDegreeEntity(save.getId(), 2));
        turndowns.saveAndFlush(new SaveTurndownEntity(save.getId(), 32, 3));

        assertThat(saveDegrees.findBySaveId(save.getId()))
                .extracting(SaveDegreeEntity::getDegreeId).containsExactlyInAnyOrder(1, 2);
        assertThat(turndowns.findBySaveIdAndJobId(save.getId(), 32))
                .isPresent().get()
                .extracting(SaveTurndownEntity::getRound).isEqualTo(3);

        saves.deleteById(save.getId());
        saves.flush();

        // ON DELETE CASCADE at the database level cleans the children.
        Integer degreeRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tblsave_degrees WHERE save_id = ?", Integer.class, save.getId());
        Integer turndownRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tblsave_turndowns WHERE save_id = ?", Integer.class, save.getId());
        assertThat(degreeRows).isZero();
        assertThat(turndownRows).isZero();
    }

    // ===== helpers =====

    private static DegreeEntity byName(List<DegreeEntity> all, String name) {
        return all.stream().filter(d -> d.getName().equals(name)).findFirst().orElseThrow();
    }

    /** Inserts an owning user (FK; tbluser is credentials-only post-V6, KAN-54). */
    private void insertUser(String name) {
        jdbc.update("INSERT INTO tbluser (name, password) VALUES (?, 'x')", name);
    }
}
