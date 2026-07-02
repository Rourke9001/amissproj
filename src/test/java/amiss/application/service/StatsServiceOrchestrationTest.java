package amiss.application.service;
import amiss.domain.model.ActionResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        JobService jobs = new JobService(jobRepo, users, education, USER);
        TimeService time = new TimeService(users, USER);
        FoodService food = new FoodService(users, USER);
        service = new StatsService(users, userStats, jobs, time, food, USER);
    }

    // ---- workMain ----------------------------------------------------------

    @Test
    void workMain_paysAndAdvancesWhenDressedWithEnoughTime() throws SQLException {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobRepo.getRequiredClothing("Janitor")).thenReturn(null); // no dress code
        when(users.getTime(USER)).thenReturn(72);
        when(jobRepo.getSalary("Janitor")).thenReturn(20);
        when(users.getDebt(USER)).thenReturn(0);
        when(users.getCash(USER)).thenReturn(100);

        ActionResult r = service.workMain();

        assertEquals("\nYou work as a Janitor and Earn R20\nYou now have R120", r.message());
        assertEquals("66h", r.timer());            // 72 - 6 = 66
        assertEquals("100", r.money());             // re-read of the (mocked) stored cash
        verify(users).updateTime(USER, 66);
        verify(userStats).incrementWork(USER);
        verify(users).updateCash(USER, 120);
    }

    @Test
    void workMain_reportsNotEnoughTimeAndChangesNothing() throws SQLException {
        when(users.getJob(USER)).thenReturn("Janitor");
        when(jobRepo.getRequiredClothing("Janitor")).thenReturn(null);
        when(users.getTime(USER)).thenReturn(5); // 5 - 6 < 0

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
        when(users.getTime(USER)).thenReturn(72);
        when(users.getCash(USER)).thenReturn(100);

        ActionResult r = service.eatMain(30);

        assertEquals("\nYou spent R30, You have R70 left\nThat Was Yummy, One point into happiness",
                r.message());
        assertEquals("71h", r.timer());            // 72 - 1 = 71
        assertEquals("100", r.money());
        verify(users).updateEat(USER, 1);           // had none -> store one
        verify(users).updateCash(USER, 70);
        verify(userStats).incrementHappiness(USER);
        verify(users).updateTime(USER, 71);
    }

    @Test
    void eatMain_reportsNotEnoughTimeAndChangesNothing() throws SQLException {
        when(users.getEat(USER)).thenReturn(0);
        when(users.getTime(USER)).thenReturn(0); // 0 - 1 < 0

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
}
