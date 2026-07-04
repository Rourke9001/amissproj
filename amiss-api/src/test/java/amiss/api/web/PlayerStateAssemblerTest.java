package amiss.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import amiss.api.web.dto.PlayerStateDto;
import amiss.application.config.ActionCosts;
import amiss.application.port.JobRepository;
import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import amiss.application.service.GameServices;
import amiss.domain.model.User;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link PlayerStateAssembler}. Wired with a <em>real</em> {@link GameServices}
 * over mocked repositories (the {@code GameServices} composition-root convention), since the
 * assembler drives every service through it exactly as the controllers do.
 */
@ExtendWith(MockitoExtension.class)
class PlayerStateAssemblerTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;
    @Mock
    private UserStatsRepository userStats;
    @Mock
    private JobRepository jobs;

    private final PlayerStateAssembler assembler = new PlayerStateAssembler();

    private GameServices services(String job) {
        User user = new User(USER, 0, 2, 3960, 120, 3, job, 1, 0, 1, 0);
        return new GameServices(user, users, userStats, jobs, ActionCosts.defaults());
    }

    private void stubCommonState(String job) throws SQLException {
        when(users.getXpos(USER)).thenReturn(0);
        when(users.getYpos(USER)).thenReturn(2);
        when(users.getTime(USER)).thenReturn(3960);
        when(users.getRound(USER)).thenReturn(3);
        when(users.getCash(USER)).thenReturn(120);
        when(users.getBank(USER)).thenReturn(50);
        when(users.getDebt(USER)).thenReturn(0);
        when(users.getRent(USER)).thenReturn(0);
        when(users.getEat(USER)).thenReturn(1);
        when(users.getJob(USER)).thenReturn(job);
        when(users.getUserClothing(USER)).thenReturn("1");
        when(userStats.getEducation(USER)).thenReturn(2);
        when(userStats.getEduprog(USER)).thenReturn(4);
        when(userStats.getHappiness(USER)).thenReturn("50");
        when(userStats.getWork(USER)).thenReturn("30");
    }

    @Test
    void assemble_unemployedPlayerHasNullJobWageAndLocation() throws SQLException {
        stubCommonState("Unemployed");

        PlayerStateDto dto = assembler.assemble(USER, services("Unemployed"));

        assertEquals("Unemployed", dto.job().name());
        assertNull(dto.job().hourlyWage());
        assertNull(dto.job().location());
    }

    @Test
    void assemble_employedPlayerReportsWageAndLocation() throws SQLException {
        stubCommonState("Cook");
        when(jobs.getSalary("Cook")).thenReturn(6);
        when(jobs.getLocation("Cook")).thenReturn("Monolith Burgers");

        PlayerStateDto dto = assembler.assemble(USER, services("Cook"));

        assertEquals("Cook", dto.job().name());
        assertEquals(6, dto.job().hourlyWage());
        assertEquals("Monolith Burgers", dto.job().location());
    }

    @Test
    void assemble_populatesStatsFoodClothingAndGoalTargets() throws SQLException {
        stubCommonState("Unemployed");

        PlayerStateDto dto = assembler.assemble(USER, services("Unemployed"));

        assertEquals(2, dto.stats().education());
        assertEquals(4, dto.stats().educationProgress());
        assertEquals(50, dto.stats().happiness());
        assertEquals(30, dto.stats().workExperience());
        assertEquals(1, dto.foodWeeks());
        assertEquals(1, dto.clothing());
        assertEquals(50, dto.bank());

        assertEquals(120, dto.goals().cash().current());
        assertEquals(1000, dto.goals().cash().target());
        assertEquals(50, dto.goals().happiness().current());
        assertEquals(200, dto.goals().happiness().target());
        assertEquals(30, dto.goals().workExperience().current());
        assertEquals(200, dto.goals().workExperience().target());
        assertEquals(2, dto.goals().education().current());
        assertEquals(8, dto.goals().education().target());
    }

    @Test
    void assemble_staleSavedPositionClampsToHome() throws SQLException {
        when(users.getXpos(USER)).thenReturn(9);
        when(users.getYpos(USER)).thenReturn(9);
        when(users.getTime(USER)).thenReturn(3960);
        when(users.getRound(USER)).thenReturn(3);
        when(users.getCash(USER)).thenReturn(120);
        when(users.getBank(USER)).thenReturn(50);
        when(users.getDebt(USER)).thenReturn(0);
        when(users.getRent(USER)).thenReturn(0);
        when(users.getEat(USER)).thenReturn(1);
        when(users.getJob(USER)).thenReturn("Unemployed");
        when(users.getUserClothing(USER)).thenReturn("1");
        when(userStats.getEducation(USER)).thenReturn(2);
        when(userStats.getEduprog(USER)).thenReturn(4);
        when(userStats.getHappiness(USER)).thenReturn("50");
        when(userStats.getWork(USER)).thenReturn("30");

        PlayerStateDto dto = assembler.assemble(USER, services("Unemployed"));

        assertEquals("LOW_COST_HOUSING", dto.location().id());
        assertEquals(0, dto.location().row());
        assertEquals(2, dto.location().col());
        assertEquals(0, dto.location().ringIndex());
    }
}
