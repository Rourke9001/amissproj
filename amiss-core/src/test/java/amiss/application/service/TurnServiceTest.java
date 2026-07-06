package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for the {@link TurnService} week rollover — the rule extracted from
 * {@code MainGameGUI} so Swing and REST share it. Wired like
 * {@link StatsServiceOrchestrationTest}: real collaborator services over mocked
 * repositories, mirroring the {@link GameServices} composition.
 *
 * <p>These pin the <em>unified</em> rent rule (a documented KAN-29 behaviour change): the
 * late-rent debt is charged whenever a rent round closes unpaid — the old Swing code only
 * charged it on re-login, and could charge it repeatedly.
 */
@ExtendWith(MockitoExtension.class)
class TurnServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;
    @Mock
    private UserStatsRepository userStats;
    @Mock
    private JobRepository jobRepo;

    private TurnService service;

    @BeforeEach
    void wireRealCollaboratorsOverMockedRepositories() {
        TimeService time = new TimeService(users, USER);
        FoodService food = new FoodService(users, USER);
        EducationService education = new EducationService(userStats, USER);
        JobService jobs = new JobService(jobRepo, users, education, time, ActionCosts.defaults(), USER);
        StatsService stats = new StatsService(users, userStats, jobs, time, food, USER);
        service = new TurnService(time, food, stats, ActionCosts.defaults());
    }

    @Test
    void endWeek_refusesAndChangesNothingWhileTimeRemains() {
        when(users.getTime(USER)).thenReturn(300);

        assertEquals(WeekSummary.weekStillRunning(), service.endWeek());

        verify(users, never()).updateRound(anyString(), anyInt());
        verify(users, never()).updateEat(anyString(), anyInt());
        verify(users, never()).updatePosition(anyString(), anyInt(), anyInt());
        verify(users, never()).addDebt(anyString(), anyInt());
        verify(users, never()).updateRent(anyString(), anyInt());
    }

    @Test
    void endWeek_fedPlayerStartsTheLongerWeekAndConsumesOneStoredFood() {
        when(users.getTime(USER)).thenReturn(0);
        when(users.getRound(USER)).thenReturn(5);
        when(users.getEat(USER)).thenReturn(2);

        WeekSummary summary = service.endWeek();

        assertEquals(new WeekSummary(true, 6, true, 4320, false, false), summary);
        verify(users).updateEat(USER, 1);          // 2 stored -> 1: consumed exactly once
        verify(users).updateTime(USER, 4320);
        verify(users).updatePosition(USER, 0, 2);   // back home at 12 o'clock
        verify(users).updateRound(USER, 6);
    }

    @Test
    void endWeek_unfedPlayerStartsTheBaseWeek() {
        when(users.getTime(USER)).thenReturn(0);
        when(users.getRound(USER)).thenReturn(1);
        when(users.getEat(USER)).thenReturn(0);

        WeekSummary summary = service.endWeek();

        assertEquals(new WeekSummary(true, 2, false, 3600, false, false), summary);
        verify(users).updateTime(USER, 3600);
        verify(users, never()).updateEat(anyString(), anyInt());
    }

    @Test
    void endWeek_chargesLateRentDebtWhenARentRoundClosesUnpaid() {
        when(users.getTime(USER)).thenReturn(0);
        when(users.getRound(USER)).thenReturn(4);
        when(users.getRent(USER)).thenReturn(1);
        when(users.getEat(USER)).thenReturn(0);

        WeekSummary summary = service.endWeek();

        assertTrue(summary.debtCharged());
        assertEquals(5, summary.round());
        assertFalse(summary.rentDue());
        verify(users).addDebt(USER, 80);
    }

    @Test
    void endWeek_chargesNoDebtWhenTheRentRoundWasPaid() {
        when(users.getTime(USER)).thenReturn(0);
        when(users.getRound(USER)).thenReturn(4);
        when(users.getRent(USER)).thenReturn(0);
        when(users.getEat(USER)).thenReturn(0);

        WeekSummary summary = service.endWeek();

        assertFalse(summary.debtCharged());
        verify(users, never()).addDebt(anyString(), anyInt());
    }

    @Test
    void endWeek_flagsRentDueOnEnteringEveryFourthRound() {
        when(users.getTime(USER)).thenReturn(0);
        when(users.getRound(USER)).thenReturn(3);
        when(users.getEat(USER)).thenReturn(0);

        WeekSummary summary = service.endWeek();

        assertTrue(summary.rentDue());
        assertEquals(4, summary.round());
        verify(users).updateRent(USER, 1);
    }

    @Test
    void endWeek_paysNoWagesAtRollover() {
        when(users.getTime(USER)).thenReturn(0);
        when(users.getRound(USER)).thenReturn(1);
        when(users.getEat(USER)).thenReturn(0);

        service.endWeek();

        verify(users, never()).updateCash(anyString(), anyInt());
    }
}
