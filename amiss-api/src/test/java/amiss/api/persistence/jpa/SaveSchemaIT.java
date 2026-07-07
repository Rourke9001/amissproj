package amiss.api.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * KAN-52: proves the V5 expand migration on a real, freshly-migrated MySQL 9 —
 * the wiki catalog seed (11 degrees / 39 jobs / degree requirements), the new
 * save tables' entity round-trips, and the user→save copy rule.
 *
 * <p>The copy rule ran at container migration time against zero users, so it is
 * exercised here by seeding a legacy user/stats pair and re-running the same
 * {@code INSERT…SELECT} (scoped to that user) — keep {@link #COPY_SQL} textually
 * in sync with V5.
 */
class SaveSchemaIT extends MySqlITSupport {

    private static final String COPY_SQL = """
            INSERT INTO tblsave (owner, label, xpos, ypos, `time`, round, cash, bank, debt,
                                 rent, eat, clothing, happiness, experience, dependability,
                                 goal_wealth, goal_happiness, goal_education, goal_career, won)
            SELECT u.name, 'Save 1', u.xpos, u.ypos, u.`time`, u.round, u.cash, u.bank, u.debt,
                   u.rent, u.eat, u.clothing, s.happiness, 10, 20,
                   50, 50, 50, 50, 0
            FROM tbluser u
            JOIN tbluserstats s ON s.name = u.name
            WHERE u.name = ?
            """;

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
        jdbc.update("DELETE FROM tbluserstats WHERE name LIKE 'it_save_%'");
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
        insertLegacyUser("it_save_alice", 250, 7);

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
        insertLegacyUser("it_save_alice", 0, 0);
        insertLegacyUser("it_save_bob", 0, 0);
        SaveEntity alices = saves.saveAndFlush(new SaveEntity("it_save_alice", "Save 1", 10, 10, 10, 10));
        saves.saveAndFlush(new SaveEntity("it_save_bob", "Save 1", 10, 10, 10, 10));

        assertThat(saves.findByOwnerOrderByUpdatedAtDesc("it_save_alice"))
                .extracting(SaveEntity::getId).containsExactly(alices.getId());
        assertThat(saves.findByIdAndOwner(alices.getId(), "it_save_bob")).isEmpty();
        assertThat(saves.findByIdAndOwner(alices.getId(), "it_save_alice")).isPresent();
    }

    @Test
    void persistsEarnedDegreesAndTurndownsAndCascadesOnSaveDelete() {
        insertLegacyUser("it_save_alice", 0, 0);
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

    // ===== the V5 user→save copy rule =====

    @Test
    void copiesALegacyAccountIntoOneStarterSave() {
        insertLegacyUser("it_save_legacy", 421, 4);

        jdbc.update(COPY_SQL, "it_save_legacy");

        List<SaveEntity> copied = saves.findByOwnerOrderByUpdatedAtDesc("it_save_legacy");
        assertThat(copied).hasSize(1);
        SaveEntity save = copied.get(0);
        assertThat(save.getLabel()).isEqualTo("Save 1");
        assertThat(save.getCash()).isEqualTo(421);   // money carried over
        assertThat(save.getRound()).isEqualTo(4);
        assertThat(save.getHappiness()).isEqualTo(33);
        assertThat(save.getJobId()).isNull();        // career reset
        assertThat(save.getExperience()).isEqualTo(10);
        assertThat(save.getDependability()).isEqualTo(20);
        assertThat(save.getGoalWealth()).isEqualTo(50);
        assertThat(save.getGoalHappiness()).isEqualTo(50);
        assertThat(save.getGoalEducation()).isEqualTo(50);
        assertThat(save.getGoalCareer()).isEqualTo(50);
        assertThat(saveDegrees.findBySaveId(save.getId())).isEmpty();   // education reset
    }

    // ===== helpers =====

    private static DegreeEntity byName(List<DegreeEntity> all, String name) {
        return all.stream().filter(d -> d.getName().equals(name)).findFirst().orElseThrow();
    }

    /** Seeds a legacy tbluser + tbluserstats pair the way pre-V5 registration did. */
    private void insertLegacyUser(String name, int cash, int round) {
        jdbc.update("""
                INSERT INTO tbluser (name, password, xpos, ypos, `time`, cash, round, job,
                                     clothing, rent, eat, debt, bank)
                VALUES (?, 'x', 0, 0, 4320, ?, ?, 'Unemployed', 1, 1, 0, 0, 0)
                """, name, cash, round == 0 ? 1 : round);
        jdbc.update("INSERT INTO tbluserstats (name, happiness, education, work, eduprog) "
                + "VALUES (?, 33, 2, 5, 0)", name);
    }
}
