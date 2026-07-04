package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.port.UserRepository;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TimeService}. The repository is mocked, so the minutes-based time
 * budget is exercised in isolation from MySQL. (Board-distance maths now lives in
 * {@code amiss.domain.board.Board} and is covered by {@code BoardTest}.)
 */
@ExtendWith(MockitoExtension.class)
class TimeServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;

    private TimeService newService() {
        return new TimeService(users, USER);
    }

    // ---- spendMinutes: minute spend + TimeSpend flags -----------------------

    @Test
    void spendMinutes_spendsAndPersistsTheRemainingMinutes() throws SQLException {
        when(users.getTime(USER)).thenReturn(4320);
        assertEquals(new TimeSpend(3960, false, false), newService().spendMinutes(360)); // 72h - 6h = 66h
        verify(users).updateTime(USER, 3960);
    }

    @Test
    void spendMinutes_zeroCostReadsTheClockUnchanged() throws SQLException {
        when(users.getTime(USER)).thenReturn(3600);
        assertEquals(new TimeSpend(3600, false, false), newService().spendMinutes(0));
        verify(users).updateTime(USER, 3600);
    }

    @Test
    void spendMinutes_flagsWeekOverWhenTheWeekIsExactlyUsedUp() throws SQLException {
        when(users.getTime(USER)).thenReturn(360);
        assertEquals(new TimeSpend(0, false, true), newService().spendMinutes(360));
        verify(users).updateTime(USER, 0);
    }

    @Test
    void spendMinutes_rejectsAndPersistsNothingWhenTimeWouldGoNegative() throws SQLException {
        when(users.getTime(USER)).thenReturn(300);
        assertEquals(new TimeSpend(300, true, false), newService().spendMinutes(360)); // 300 - 360 < 0
        verify(users, never()).updateTime(anyString(), anyInt());
    }

    @Test
    void spendMinutes_rejectionOnAnEmptyClockAlsoReportsWeekOver() throws SQLException {
        when(users.getTime(USER)).thenReturn(0);
        assertEquals(new TimeSpend(0, true, true), newService().spendMinutes(360));
        verify(users, never()).updateTime(anyString(), anyInt());
    }

    @Test
    void spendMinutes_rejectsWhenNoRowExists() throws SQLException {
        when(users.getTime(USER)).thenReturn(-1);
        assertEquals(new TimeSpend(0, true, false), newService().spendMinutes(360));
        verify(users, never()).updateTime(anyString(), anyInt());
    }

    @Test
    void spendMinutes_rejectsOnSqlException() throws SQLException {
        when(users.getTime(USER)).thenThrow(new SQLException("boom"));
        assertEquals(new TimeSpend(0, true, false), newService().spendMinutes(360));
    }

    // ---- format / readClock -------------------------------------------------

    @Test
    void format_rendersHoursAndMinutes() {
        assertEquals("38h 30m", TimeService.format(2310));
    }

    @Test
    void format_omitsTheMinutesPartWhenZero() {
        assertEquals("72h", TimeService.format(4320));
    }

    @Test
    void format_rendersUnderAnHourAsMinutesOnly() {
        assertEquals("45m", TimeService.format(45));
    }

    @Test
    void format_rendersAnEmptyClockAsZeroHours() {
        assertEquals("0h", TimeService.format(0));
    }

    @Test
    void readClock_formatsTheCurrentClockWithoutSpending() throws SQLException {
        when(users.getTime(USER)).thenReturn(2310);
        assertEquals("38h 30m", newService().readClock());
        verify(users).updateTime(USER, 2310);
    }

    // ---- position / round reads + writes -----------------------------------

    @Test
    void getX_returnsZeroOnSqlException() throws SQLException {
        when(users.getXpos(USER)).thenThrow(new SQLException("boom"));
        assertEquals(0, newService().getX());
    }

    @Test
    void getY_returnsZeroOnSqlException() throws SQLException {
        when(users.getYpos(USER)).thenThrow(new SQLException("boom"));
        assertEquals(0, newService().getY());
    }

    @Test
    void getRound_returnsTheStoredRound() throws SQLException {
        when(users.getRound(USER)).thenReturn(5);
        assertEquals("5", newService().getRound());
    }

    @Test
    void getRound_returnsFailureSentinelWhenNoRowExists() throws SQLException {
        when(users.getRound(USER)).thenReturn(-1);
        assertEquals("failed to get round", newService().getRound());
    }

    @Test
    void setRound_incrementsAndPersistsTheStoredRound() throws SQLException {
        when(users.getRound(USER)).thenReturn(5);
        newService().setRound();
        verify(users).updateRound(USER, 6);
    }

    @Test
    void toString_rendersCurrentPositionAsXColonY() throws SQLException {
        when(users.getXpos(USER)).thenReturn(2);
        when(users.getYpos(USER)).thenReturn(3);
        assertEquals("2:3", newService().toString());
    }
}
