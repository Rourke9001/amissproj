package amiss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.repository.UserRepository;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TimeService}. The repository is mocked, so the board-distance
 * maths and the time formatting are exercised in isolation from MySQL.
 *
 * <p>These are characterization tests: they lock in the current behaviour (including a
 * couple of quirks) rather than asserting what "should" happen.
 */
@ExtendWith(MockitoExtension.class)
class TimeServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;

    private TimeService newService() {
        return new TimeService(users, USER);
    }

    /** Fixes the player's current board position (the "old" cell getMulti measures from). */
    private void atPosition(int x, int y) throws SQLException {
        when(users.getXpos(USER)).thenReturn(x);
        when(users.getYpos(USER)).thenReturn(y);
    }

    // ---- getMulti: board-distance maths ------------------------------------
    // The board is a 4x4 grid of cells indexed 0..3. The base cost is the Manhattan
    // distance; certain "knight-ish" / straight-line moves between interior cells
    // (avoiding the 0 and 3 edges) cost +2. Edge cells never get the bonus.

    @Test
    void getMulti_isZeroForTheSameCell() throws SQLException {
        atPosition(2, 2);
        assertEquals(0, newService().getMulti(2, 2));
    }

    @Test
    void getMulti_isPlainManhattanWhenNoBonusApplies() throws SQLException {
        atPosition(1, 1);
        // |1-1| + |1-2| = 1, and none of the +2 conditions fire.
        assertEquals(1, newService().getMulti(1, 2));
    }

    @Test
    void getMulti_addsBonusForAOneByThreeInteriorMove() throws SQLException {
        atPosition(1, 0);
        // target (2,3): |1-2|=1 and |0-3|=3 with both rows interior -> base 4 + 2 = 6.
        assertEquals(6, newService().getMulti(2, 3));
    }

    @Test
    void getMulti_addsBonusForAThreeByOneInteriorMove() throws SQLException {
        atPosition(0, 1);
        // target (3,2): |1-2|=1 (cols) and |0-3|=3 (rows) with both cols interior -> 4 + 2 = 6.
        assertEquals(6, newService().getMulti(3, 2));
    }

    @Test
    void getMulti_addsBonusForAStraightThreeMoveDownAnInteriorColumn() throws SQLException {
        atPosition(0, 1);
        // target (3,1): same interior column 1, rows differ by 3 -> base 3 + 2 = 5.
        assertEquals(5, newService().getMulti(3, 1));
    }

    @Test
    void getMulti_addsBonusForAStraightThreeMoveAlongAnInteriorRow() throws SQLException {
        atPosition(1, 0);
        // target (1,3): same interior row 1, cols differ by 3 -> base 3 + 2 = 5.
        assertEquals(5, newService().getMulti(1, 3));
    }

    @Test
    void getMulti_givesNoBonusWhenTheMoveTouchesAnEdgeCell() throws SQLException {
        atPosition(0, 0);
        // target (1,3): a 1-by-3 move, but oldRow is on edge 0 so the bonus is suppressed.
        assertEquals(4, newService().getMulti(1, 3));
    }

    // ---- getNewTime: time spend + "H:MM" formatting ------------------------

    @Test
    void getNewTime_subtractsTenPerStepAndFormatsWithPaddedZeroMinutes() throws SQLException {
        when(users.getTime(USER)).thenReturn(720);
        // 720 - 6*10 = 660 -> 11h 00m. mins==0 is padded to "00".
        assertEquals("11:00", newService().getNewTime(6));
        verify(users).updateTime(USER, 660);
    }

    @Test
    void getNewTime_formatsTwoDigitMinutes() throws SQLException {
        when(users.getTime(USER)).thenReturn(720);
        // 720 - 1*10 = 710 -> 11h 50m.
        assertEquals("11:50", newService().getNewTime(1));
        verify(users).updateTime(USER, 710);
    }

    @Test
    void getNewTime_doesNotZeroPadSingleDigitMinutes() throws SQLException {
        when(users.getTime(USER)).thenReturn(135);
        // 135 - 10 = 125 -> 2h 5m. QUIRK: only mins==0 is padded, so this renders "2:5"
        // (not "2:05"). Characterization test — documents current behaviour, not ideal.
        assertEquals("2:5", newService().getNewTime(1));
        verify(users).updateTime(USER, 125);
    }

    @Test
    void getNewTime_returnsNotEnoughTimeAndDoesNotPersistWhenTimeWouldGoNegative() throws SQLException {
        when(users.getTime(USER)).thenReturn(50);
        // 50 - 6*10 = -10 -> rejected, and the time is NOT written back.
        assertEquals("Not Enough Time", newService().getNewTime(6));
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
        atPosition(2, 3);
        assertEquals("2:3", newService().toString());
    }
}
