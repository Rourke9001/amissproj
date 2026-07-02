package amiss.domain.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoardTest {

    private final Board board = new Board();

    @Test
    void hasThirteenStops() {
        assertEquals(13, board.size());
    }

    @Test
    void startCellIsLowCostHousing() {
        assertEquals(Location.LOW_COST_HOUSING, board.locationAt(0, 0));
        assertEquals(0, board.ringIndex(0, 0));
    }

    @Test
    void mapsStopsToTheOriginalClockwiseOrder() {
        assertEquals(Location.PAWN_SHOP, board.locationAt(0, 1));
        assertEquals(Location.Z_MART, board.locationAt(0, 2));
        assertEquals(Location.MONOLITH_BURGERS, board.locationAt(0, 3));
        assertEquals(Location.QT_CLOTHING, board.locationAt(0, 4));
        assertEquals(Location.SOCKET_CITY, board.locationAt(1, 4));
        assertEquals(Location.HI_TECH_U, board.locationAt(2, 4));
        assertEquals(Location.EMPLOYMENT_OFFICE, board.locationAt(3, 4));
        assertEquals(Location.FACTORY, board.locationAt(3, 3));
        assertEquals(Location.BANK, board.locationAt(3, 1));
        assertEquals(Location.BLACKS_MARKET, board.locationAt(3, 0));
        assertEquals(Location.LE_SECURITY_APARTMENTS, board.locationAt(2, 0));
        assertEquals(Location.RENT_OFFICE, board.locationAt(1, 0));
    }

    @Test
    void timerCellIsNotAStop() {
        assertTrue(board.isTimerCell(3, 2));
        assertFalse(board.isStop(3, 2));
        assertNull(board.locationAt(3, 2));
    }

    @Test
    void interiorCellsAreNotStops() {
        assertFalse(board.isStop(1, 1));
        assertFalse(board.isStop(1, 2));
        assertFalse(board.isStop(2, 1));
        assertFalse(board.isStop(2, 3));
        assertEquals(-1, board.ringIndex(1, 1));
    }

    @Test
    void ringDistanceIsShorterOfTheTwoDirections() {
        assertEquals(0, board.ringDistance(4, 4));
        assertEquals(1, board.ringDistance(0, 1));
        assertEquals(1, board.ringDistance(0, 12));   // wrap-around: start and Rent Office are neighbours
        assertEquals(6, board.ringDistance(0, 6));     // min(6, 7)
        assertEquals(6, board.ringDistance(0, 7));     // min(7, 6)
    }

    @Test
    void ringDistanceBetweenCells() {
        // (0,0) start -> (3,3) Factory: indices 0 and 8 -> min(8, 5) = 5
        assertEquals(5, board.ringDistanceBetween(0, 0, 3, 3));
        // (0,0) -> (1,0) Rent Office: indices 0 and 12 -> 1
        assertEquals(1, board.ringDistanceBetween(0, 0, 1, 0));
    }

    @Test
    void distanceToANonStopCellIsZero() {
        assertEquals(0, board.ringDistanceBetween(0, 0, 3, 2)); // to timer cell
    }
}
