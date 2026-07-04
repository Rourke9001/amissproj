package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Tests for {@link RentService#payRent()}, extracted verbatim from
 * {@code RentOfficeGUI.btnRentActionPerformed}. Wired like {@link TurnServiceTest}: real
 * collaborator services over mocked repositories, mirroring the {@link GameServices}
 * composition. Every rejection path must change nothing (no time/cash/rent-flag writes).
 */
@ExtendWith(MockitoExtension.class)
class RentServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;
    @Mock
    private UserStatsRepository userStats;
    @Mock
    private JobRepository jobRepo;

    private RentService service;

    @BeforeEach
    void wireRealCollaboratorsOverMockedRepositories() {
        TimeService time = new TimeService(users, USER);
        FoodService food = new FoodService(users, USER);
        EducationService education = new EducationService(userStats, USER);
        JobService jobs = new JobService(jobRepo, users, education, USER);
        StatsService stats = new StatsService(users, userStats, jobs, time, food, USER);
        service = new RentService(time, stats, ActionCosts.defaults());
    }

    /**
     * A rejection must not change cash or the rent flag. {@code updateTime} is not asserted
     * here: even a zero-cost {@code spendMinutes(0)} clock read writes the (unchanged) time
     * back, the same idiom {@code TurnServiceTest} accepts for its "time remains" case.
     */
    private void assertNothingCharged() throws SQLException {
        verify(users, never()).updateCash(anyString(), anyInt());
        verify(users, never()).updateRent(anyString(), anyInt());
    }

    @Test
    void payRent_notDueOutsideTheFourthRound() throws SQLException {
        when(users.getRound(USER)).thenReturn(3);
        when(users.getTime(USER)).thenReturn(4320);
        when(users.getCash(USER)).thenReturn(100);

        RentPayment result = service.payRent();

        assertEquals(new RentPayment(RentPayment.Status.NOT_DUE, 4320, 100), result);
        assertNothingCharged();
    }

    @Test
    void payRent_notDueWhenAlreadyPaidThisRound() throws SQLException {
        when(users.getRound(USER)).thenReturn(4);
        when(users.getRent(USER)).thenReturn(0);
        when(users.getTime(USER)).thenReturn(4320);
        when(users.getCash(USER)).thenReturn(100);

        RentPayment result = service.payRent();

        assertEquals(RentPayment.Status.NOT_DUE, result.status());
        assertNothingCharged();
    }

    @Test
    void payRent_weekOverRejectsAndChangesNothing() throws SQLException {
        when(users.getRound(USER)).thenReturn(4);
        when(users.getRent(USER)).thenReturn(1);
        when(users.getTime(USER)).thenReturn(0);
        when(users.getCash(USER)).thenReturn(100);

        RentPayment result = service.payRent();

        assertEquals(new RentPayment(RentPayment.Status.WEEK_OVER, 0, 100), result);
        assertNothingCharged();
    }

    @Test
    void payRent_insufficientCashRejectsAndChangesNothing() throws SQLException {
        when(users.getRound(USER)).thenReturn(4);
        when(users.getRent(USER)).thenReturn(1);
        when(users.getTime(USER)).thenReturn(300);
        when(users.getCash(USER)).thenReturn(50); // < WEEKLY_RENT (80)

        RentPayment result = service.payRent();

        assertEquals(new RentPayment(RentPayment.Status.INSUFFICIENT_CASH, 300, 50), result);
        assertNothingCharged();
    }

    @Test
    void payRent_insufficientTimeRejectsAndChangesNothing() throws SQLException {
        when(users.getRound(USER)).thenReturn(4);
        when(users.getRent(USER)).thenReturn(1);
        when(users.getTime(USER)).thenReturn(50); // < payRentMinutes (120)
        when(users.getCash(USER)).thenReturn(100);

        RentPayment result = service.payRent();

        assertEquals(new RentPayment(RentPayment.Status.INSUFFICIENT_TIME, 50, 100), result);
        assertNothingCharged();
    }

    @Test
    void payRent_paysAndClearsTheRentFlagWhenDueAndAffordable() throws SQLException {
        when(users.getRound(USER)).thenReturn(4);
        when(users.getRent(USER)).thenReturn(1);
        when(users.getTime(USER)).thenReturn(4320);
        when(users.getCash(USER)).thenReturn(100);

        RentPayment result = service.payRent();

        assertEquals(RentPayment.Status.OK, result.status());
        assertEquals(4200, result.remainingMinutes()); // 4320 - 120
        verify(users).updateTime(USER, 4200);
        verify(users).updateRent(USER, 0);
        verify(users).updateCash(USER, 20); // 100 - 80
    }
}
