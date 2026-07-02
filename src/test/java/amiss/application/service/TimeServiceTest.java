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
 * Unit tests for {@link TimeService}. The repository is mocked, so the hours-based time
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

    // ---- getNewTime: hours spend + "Nh" formatting -------------------------

    @Test
    void getNewTime_spendsWholeHoursAndFormatsWithAnHSuffix() throws SQLException {
        when(users.getTime(USER)).thenReturn(72);
        assertEquals("66h", newService().getNewTime(6)); // 72 - 6 = 66
        verify(users).updateTime(USER, 66);
    }

    @Test
    void getNewTime_spendsASingleHour() throws SQLException {
        when(users.getTime(USER)).thenReturn(60);
        assertEquals("59h", newService().getNewTime(1)); // 60 - 1 = 59
        verify(users).updateTime(USER, 59);
    }

    @Test
    void getNewTime_zeroCostReadsTheClockAndReturnsItUnchanged() throws SQLException {
        when(users.getTime(USER)).thenReturn(60);
        assertEquals("60h", newService().getNewTime(0));
        verify(users).updateTime(USER, 60);
    }

    @Test
    void getNewTime_returnsZeroHoursWhenTheWeekIsExactlyUsedUp() throws SQLException {
        when(users.getTime(USER)).thenReturn(6);
        assertEquals("0h", newService().getNewTime(6)); // 6 - 6 = 0 -> round-ended sentinel
        verify(users).updateTime(USER, 0);
    }

    @Test
    void getNewTime_returnsNotEnoughTimeAndDoesNotPersistWhenTimeWouldGoNegative() throws SQLException {
        when(users.getTime(USER)).thenReturn(5);
        assertEquals("Not Enough Time", newService().getNewTime(6)); // 5 - 6 < 0 -> rejected
        verify(users, never()).updateTime(anyString(), anyInt());
    }

    @Test
    void getNewTime_returnsFailureSentinelWhenNoRowExists() throws SQLException {
        when(users.getTime(USER)).thenReturn(-1);
        assertEquals("failed to get time", newService().getNewTime(6));
    }

    @Test
    void getNewTime_returnsFailureSentinelOnSqlException() throws SQLException {
        when(users.getTime(USER)).thenThrow(new SQLException("boom"));
        assertEquals("failed to get time", newService().getNewTime(6));
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
