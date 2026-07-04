package amiss.application.service;
import amiss.domain.model.ActionResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.config.ActionCosts;
import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for the {@link StatsService#workMain()} / {@link StatsService#eatMain(int)}
 * orchestration. These exercise the full decision tree across the collaborating
 * services, so they are wired with <em>real</em> {@link JobService}/{@link TimeService}/
 * {@link FoodService}/{@link EducationService} instances over <em>mocked</em>
 * repositories — mirroring how {@link GameServices} composes them in production.
 *
 * <p>(Real collaborators are used rather than mocks because {@code workMain} calls
 * {@code job.toString()}, which Mockito cannot stub.)
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceOrchestrationTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;
    @Mock
    private UserStatsRepository userStats;
    @Mock
    private JobRepository jobRepo;

    private StatsService service;

    @BeforeEach
    void wireRealCollaboratorsOverMockedRepositories() {
        EducationService education = new EducationService(userStats, USER);
        TimeService time = new TimeService(users, USER);
        JobService jobs = new JobService(jobRepo, users, education, time, ActionCosts.defaults(), USER);
        FoodService food = new FoodService(users, USER);
        service = new StatsService(users, userStats, jobs, time, food, USER);
    }

    // ---- workMain ----------------------------------------------------------

    @Test
    void workMain_paysAndAdvancesWhenDressedWithEnoughTime() throws SQLException {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobRepo.getRequiredClothing("Janitor")).thenReturn(null); // no dress code
        when(users.getTime(USER)).thenReturn(4320);
        when(jobRepo.getSalary("Janitor")).thenReturn(20);
        when(users.getDebt(USER)).thenReturn(0);
        when(users.getCash(USER)).thenReturn(100);

        ActionResult r = service.workMain();

        assertEquals("\nYou work as a Janitor and Earn R20\nYou now have R120", r.message());
        assertEquals("66h", r.timer());            // 4320 - 360 = 3960 min = 66h
        assertEquals("100", r.money());             // re-read of the (mocked) stored cash
        verify(users).updateTime(USER, 3960);
        verify(userStats).incrementWork(USER);
        verify(users).updateCash(USER, 120);
    }

    @Test
    void workMain_reportsNotEnoughTimeAndChangesNothing() throws SQLException {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobRepo.getRequiredClothing("Janitor")).thenReturn(null);
        when(users.getTime(USER)).thenReturn(300); // 300 - 360 < 0

        ActionResult r = service.workMain();

        assertEquals("\nNot Enough Time", r.message());
        assertNull(r.timer());
        assertNull(r.money());
        verify(users, never()).updateTime(anyString(), anyInt());
        verify(userStats, never()).incrementWork(anyString());
        verify(users, never()).updateCash(anyString(), anyInt());
    }

    @Test
    void workMain_refusesWhenUnderdressed() throws SQLException {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobRepo.getRequiredClothing("Pilot")).thenReturn("3");
        when(users.getUserClothing(USER)).thenReturn("1");

        ActionResult r = service.workMain();

        assertEquals("\n\nYou are not properly dressed for work", r.message());
        assertNull(r.timer());
        assertNull(r.money());
        verify(users, never()).updateTime(anyString(), anyInt());
        verify(userStats, never()).incrementWork(anyString());
        verify(users, never()).updateCash(anyString(), anyInt());
    }

    // ---- eatMain -----------------------------------------------------------

    @Test
    void eatMain_buysFoodAndAddsHappinessOnSuccess() throws SQLException {
        when(users.getEat(USER)).thenReturn(0);
        when(users.getTime(USER)).thenReturn(4320);
        when(users.getCash(USER)).thenReturn(100);

        ActionResult r = service.eatMain(30);

        assertEquals("\nYou spent R30, You have R70 left\nThat Was Yummy, One point into happiness",
                r.message());
        assertEquals("71h", r.timer());            // 4320 - 60 = 4260 min = 71h
        assertEquals("100", r.money());
        verify(users).updateEat(USER, 1);           // had none -> store one
        verify(users).updateCash(USER, 70);
        verify(userStats).incrementHappiness(USER);
        verify(users).updateTime(USER, 4260);
    }

    @Test
    void eatMain_reportsNotEnoughTimeAndChangesNothing() throws SQLException {
        when(users.getEat(USER)).thenReturn(0);
        when(users.getTime(USER)).thenReturn(0); // 0 - 60 < 0

        ActionResult r = service.eatMain(30);

        assertEquals("\nNot Enough Time", r.message());
        assertNull(r.timer());
        assertNull(r.money());
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(userStats, never()).incrementHappiness(anyString());
    }

    @Test
    void eatMain_reportsNotEnoughCashWithoutBuying() throws SQLException {
        when(users.getEat(USER)).thenReturn(0);
        when(users.getTime(USER)).thenReturn(72);
        when(users.getCash(USER)).thenReturn(10); // 10 < price 50

        ActionResult r = service.eatMain(50);

        assertEquals("\nNot Enough Cash, You only have R10", r.message());
        assertNull(r.timer());
        assertNull(r.money());
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(userStats, never()).incrementHappiness(anyString());
        verify(users, never()).updateEat(anyString(), anyInt());
    }

    // ---- work ----------------------------------------------------------------

    @Test
    void work_paysAndDocksDebtWhenInDebt() throws SQLException {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobRepo.getRequiredClothing("Janitor")).thenReturn(null);
        when(users.getTime(USER)).thenReturn(4320);
        when(jobRepo.getSalary("Janitor")).thenReturn(20);
        when(users.getDebt(USER)).thenReturn(50);
        when(users.getCash(USER)).thenReturn(100);

        WorkOutcome outcome = service.work();

        assertEquals(WorkOutcome.Status.OK, outcome.status());
        assertEquals(3960, outcome.remainingMinutes()); // 4320 - 360
        assertEquals(110, outcome.cash());              // 100 + 20 - 10 rent penalty
        assertEquals("Janitor", outcome.jobName());
        assertEquals(20, outcome.hourlyWage());
        assertTrue(outcome.debtDocked());
        verify(users).subtractDebt(USER, 10);
        verify(users).updateCash(USER, 110);
    }

    @Test
    void work_reportsInsufficientTimeAndChangesNothing() throws SQLException {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobRepo.getRequiredClothing("Janitor")).thenReturn(null);
        when(users.getTime(USER)).thenReturn(300); // 300 - 360 < 0

        WorkOutcome outcome = service.work();

        assertEquals(WorkOutcome.Status.INSUFFICIENT_TIME, outcome.status());
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(userStats, never()).incrementWork(anyString());
    }

    @Test
    void work_refusesWhenUnderdressedAndChargesNothing() throws SQLException {
        when(users.getJob(USER)).thenReturn("Pilot");
        when(jobRepo.getRequiredClothing("Pilot")).thenReturn("3");
        when(users.getUserClothing(USER)).thenReturn("1");

        WorkOutcome outcome = service.work();

        assertEquals(WorkOutcome.Status.UNDERDRESSED, outcome.status());
        verify(users, never()).updateTime(anyString(), anyInt());
        verify(users, never()).updateCash(anyString(), anyInt());
    }

    // ---- eat -------------------------------------------------------------------

    @Test
    void eat_buysFoodAndAddsHappinessOnSuccess() throws SQLException {
        when(users.getEat(USER)).thenReturn(0);
        when(users.getTime(USER)).thenReturn(4320);
        when(users.getCash(USER)).thenReturn(100);

        EatOutcome outcome = service.eat(30);

        assertEquals(EatOutcome.Status.OK, outcome.status());
        assertEquals(4260, outcome.remainingMinutes());
        assertEquals(70, outcome.cash());
        verify(users).updateEat(USER, 1);
        verify(users).updateCash(USER, 70);
        verify(userStats).incrementHappiness(USER);
    }

    @Test
    void eat_reportsInsufficientCashButStillChargesTheHour() throws SQLException {
        when(users.getEat(USER)).thenReturn(0);
        when(users.getTime(USER)).thenReturn(72);
        when(users.getCash(USER)).thenReturn(10); // 10 < price 50

        EatOutcome outcome = service.eat(50);

        assertEquals(EatOutcome.Status.INSUFFICIENT_CASH, outcome.status());
        assertEquals(10, outcome.cash());
        verify(users).updateTime(USER, 12); // 72 - 60: the hour is charged even though cash failed
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(users, never()).updateEat(anyString(), anyInt());
    }

    // ---- buyGroceries ------------------------------------------------------------

    @Test
    void buyGroceries_succeedsAndAddsFoodWeeks() throws SQLException {
        when(users.getTime(USER)).thenReturn(4320);
        when(users.getCash(USER)).thenReturn(100);
        when(users.getEat(USER)).thenReturn(0);

        PurchaseOutcome outcome = service.buyGroceries(25, 1);

        assertEquals(PurchaseOutcome.Status.OK, outcome.status());
        assertEquals(75, outcome.cash());
        verify(users).updateCash(USER, 75);
        verify(users).updateEat(USER, 1);
    }

    @Test
    void buyGroceries_insufficientCashRejectsAndChangesNothing() throws SQLException {
        when(users.getTime(USER)).thenReturn(4320);
        when(users.getCash(USER)).thenReturn(10); // 10 < price 25

        PurchaseOutcome outcome = service.buyGroceries(25, 1);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_CASH, outcome.status());
        assertEquals(10, outcome.cash());
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(users, never()).updateEat(anyString(), anyInt());
    }

    @Test
    void buyGroceries_insufficientTimeRejectsAndChangesNothing() throws SQLException {
        // Swing's shopMinutes default is 0 (always affordable time-wise); override it here
        // to exercise the branch a deployment-configured non-zero shop cost would reach.
        ActionCosts nonZeroShop = new ActionCosts(360, 360, 360, 240, 120, 60, 100, 40, 120, 3600, 4320);
        EducationService education = new EducationService(userStats, USER);
        TimeService time = new TimeService(users, USER);
        JobService jobs = new JobService(jobRepo, users, education, time, nonZeroShop, USER);
        FoodService food = new FoodService(users, USER);
        StatsService withShopCost = new StatsService(users, userStats, jobs, time, food, nonZeroShop, USER);
        when(users.getTime(USER)).thenReturn(50); // 50 - 100 < 0

        PurchaseOutcome outcome = withShopCost.buyGroceries(25, 1);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_TIME, outcome.status());
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(users, never()).updateEat(anyString(), anyInt());
    }

    // ---- buyClothes (incl. the free-clothes-on-failed-purchase bug fix) ---------

    @Test
    void buyClothes_succeedsAndUpdatesClothingLevel() throws SQLException {
        when(users.getTime(USER)).thenReturn(4320);
        when(users.getCash(USER)).thenReturn(100);

        PurchaseOutcome outcome = service.buyClothes(3, 55);

        assertEquals(PurchaseOutcome.Status.OK, outcome.status());
        assertEquals(45, outcome.cash());
        verify(users).updateCash(USER, 45);
        verify(users).updateClothing(USER, 3);
    }

    @Test
    void buyClothes_weekOverRejectsAndDoesNotGiveFreeClothes() throws SQLException {
        when(users.getTime(USER)).thenReturn(0); // week over

        PurchaseOutcome outcome = service.buyClothes(3, 55);

        assertEquals(PurchaseOutcome.Status.WEEK_OVER, outcome.status());
        // Regression: the old ClothesStoreGUI called setClothes() before this check, so a
        // failed purchase still upgraded the player's clothes for free. It must not anymore.
        verify(users, never()).updateClothing(anyString(), anyInt());
        verify(users, never()).updateCash(anyString(), anyInt());
    }

    @Test
    void buyClothes_insufficientCashRejectsAndDoesNotGiveFreeClothes() throws SQLException {
        when(users.getTime(USER)).thenReturn(4320);
        when(users.getCash(USER)).thenReturn(10); // 10 < price 55

        PurchaseOutcome outcome = service.buyClothes(3, 55);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_CASH, outcome.status());
        assertEquals(10, outcome.cash());
        // Regression: same bug as above, reached via the cash guard instead of week-over.
        verify(users, never()).updateClothing(anyString(), anyInt());
        verify(users, never()).updateCash(anyString(), anyInt());
    }

    @Test
    void buyClothes_insufficientTimeRejectsAndDoesNotGiveFreeClothes() throws SQLException {
        ActionCosts nonZeroShop = new ActionCosts(360, 360, 360, 240, 120, 60, 100, 40, 120, 3600, 4320);
        EducationService education = new EducationService(userStats, USER);
        TimeService time = new TimeService(users, USER);
        JobService jobs = new JobService(jobRepo, users, education, time, nonZeroShop, USER);
        FoodService food = new FoodService(users, USER);
        StatsService withShopCost = new StatsService(users, userStats, jobs, time, food, nonZeroShop, USER);
        when(users.getTime(USER)).thenReturn(50); // clock read (0-cost) OK, then 50 - 100 < 0
        when(users.getCash(USER)).thenReturn(100);

        PurchaseOutcome outcome = withShopCost.buyClothes(3, 55);

        assertEquals(PurchaseOutcome.Status.INSUFFICIENT_TIME, outcome.status());
        verify(users, never()).updateClothing(anyString(), anyInt());
        verify(users, never()).updateCash(anyString(), anyInt());
    }
}
