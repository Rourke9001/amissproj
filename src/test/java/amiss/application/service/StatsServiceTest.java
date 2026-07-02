package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.port.UserRepository;
import amiss.application.port.UserStatsRepository;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link StatsService}'s money / rent / debt / experience rules. The
 * repositories are mocked; the collaborating services ({@code job}/{@code dist}/
 * {@code eat}) are unused by these methods, so they are inert mocks. The work/eat
 * orchestration is covered separately in {@link StatsServiceOrchestrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;
    @Mock
    private UserStatsRepository stats;
    @Mock
    private JobService job;
    @Mock
    private TimeService dist;
    @Mock
    private FoodService eat;

    private StatsService newService() {
        return new StatsService(users, stats, job, dist, eat, USER);
    }

    // ---- buy ---------------------------------------------------------------

    @Test
    void buy_deductsThePriceAndPersistsWhenAffordable() throws SQLException {
        when(users.getCash(USER)).thenReturn(100);
        assertEquals("You spent R30, You have R70 left", newService().buy("30"));
        verify(users).updateCash(USER, 70);
    }

    @Test
    void buy_rejectsAndDoesNotPersistWhenTooExpensive() throws SQLException {
        when(users.getCash(USER)).thenReturn(20);
        assertEquals("not enough cash, you only have R20", newService().buy("30"));
        verify(users, never()).updateCash(anyString(), anyInt());
    }

    @Test
    void buy_treatsANonNumericPriceAsZero() throws SQLException {
        when(users.getCash(USER)).thenReturn(100);
        // Validation.parseIntOrDefault("abc", 0) -> 0, so nothing is actually spent.
        assertEquals("You spent R0, You have R100 left", newService().buy("abc"));
        verify(users).updateCash(USER, 100);
    }

    @Test
    void buy_reportsFailureWhenPersistenceThrows() throws SQLException {
        when(users.getCash(USER)).thenReturn(100);
        doThrow(new SQLException("boom")).when(users).updateCash(USER, 70);
        assertEquals("failed to purchase", newService().buy("30"));
    }

    // ---- getCash -----------------------------------------------------------

    @Test
    void getCash_returnsTheStoredCash() throws SQLException {
        when(users.getCash(USER)).thenReturn(100);
        assertEquals(100, newService().getCash());
    }

    @Test
    void getCash_returnsMinusOneOnSqlException() throws SQLException {
        when(users.getCash(USER)).thenThrow(new SQLException("boom"));
        assertEquals(-1, newService().getCash());
    }

    // ---- setCash: rent-arrears penalty branch ------------------------------

    @Test
    void setCash_deductsRentArrearsAndPaysDownDebtWhenInDebt() throws SQLException {
        when(users.getDebt(USER)).thenReturn(50);
        when(users.getCash(USER)).thenReturn(100);

        // earn 20 + cash 100 - R10 rent penalty = 110.
        assertEquals("You were deducted R10 for not paying rent \nYou now have R110",
                newService().setCash(20));
        verify(users).subtractDebt(USER, 10);
        verify(users).updateCash(USER, 110);
    }

    @Test
    void setCash_justAddsEarningsWhenNotInDebt() throws SQLException {
        when(users.getDebt(USER)).thenReturn(0);
        when(users.getCash(USER)).thenReturn(100);

        assertEquals("You now have R120", newService().setCash(20));
        verify(users).updateCash(USER, 120);
        verify(users, never()).subtractDebt(anyString(), anyInt());
    }

    @Test
    void setCash_reportsFailureWhenPersistenceThrows() throws SQLException {
        when(users.getDebt(USER)).thenReturn(0);
        when(users.getCash(USER)).thenReturn(100);
        doThrow(new SQLException("boom")).when(users).updateCash(USER, 120);
        assertEquals("failed to update cash", newService().setCash(20));
    }

    // ---- rent / debt / experience / happiness ------------------------------

    @Test
    void getRent_returnsTheStoredRentStatus() throws SQLException {
        when(users.getRent(USER)).thenReturn(1);
        assertEquals(1, newService().getRent());
    }

    @Test
    void setRent_persistsTheRentStatus() throws SQLException {
        newService().setRent(0);
        verify(users).updateRent(USER, 0);
    }

    @Test
    void getDebt_returnsTheStoredDebt() throws SQLException {
        when(users.getDebt(USER)).thenReturn(0);
        assertEquals(0, newService().getDebt());
    }

    @Test
    void setDebt_addsToTheStoredDebt() throws SQLException {
        newService().setDebt(50);
        verify(users).addDebt(USER, 50);
    }

    @Test
    void payDebt_subtractsTenFromTheStoredDebt() throws SQLException {
        newService().payDebt();
        verify(users).subtractDebt(USER, 10);
    }

    @Test
    void updateWork_incrementsWorkExperience() throws SQLException {
        newService().updateWork();
        verify(stats).incrementWork(USER);
    }

    @Test
    void getWork_returnsFallbackStringOnSqlException() throws SQLException {
        when(stats.getWork(USER)).thenThrow(new SQLException("boom"));
        assertEquals("Failed to get work", newService().getWork());
    }

    @Test
    void updateHappiness_incrementsHappiness() throws SQLException {
        newService().updateHappiness();
        verify(stats).incrementHappiness(USER);
    }

    @Test
    void getHappiness_returnsTheStoredHappiness() throws SQLException {
        when(stats.getHappiness(USER)).thenReturn("3");
        assertEquals("3", newService().getHappiness());
    }

    // ---- reset (start the game over) ---------------------------------------

    @Test
    void reset_resetsBothTheStatsAndTheUserRows() throws SQLException {
        newService().reset();
        verify(stats).resetStats(USER);
        verify(users).resetUser(USER);
    }
}
