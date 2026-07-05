package amiss.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import amiss.application.config.ActionCosts;
import amiss.application.port.UserRepository;
import amiss.domain.board.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link TravelService} over the default costs (40 min/ring-step + 120 min
 * building entry). The repository is mocked; positions are the real {@code Board} cells —
 * home / Low-Cost Housing is ring index 0 at {@code (0,2)}.
 */
@ExtendWith(MockitoExtension.class)
class TravelServiceTest {

    private static final String USER = "bob";

    @Mock
    private UserRepository users;

    private TravelService service;

    @BeforeEach
    void wire() {
        service = new TravelService(new TimeService(users, USER), ActionCosts.defaults());
    }

    private void playerAtHomeWithMinutes(int minutes) {
        when(users.getXpos(USER)).thenReturn(0);
        when(users.getYpos(USER)).thenReturn(2);
        when(users.getTime(USER)).thenReturn(minutes);
    }

    @Test
    void moveTo_oneStepClockwiseCostsAStepPlusEntry() {
        playerAtHomeWithMinutes(4320);

        MoveResult result = service.moveTo(Location.PAWN_SHOP); // ring index 1

        assertEquals(new MoveResult(MoveResult.Status.OK, 1, 160, 4160), result);
        verify(users).updateTime(USER, 4160);
        verify(users).updatePosition(USER, 0, 3);
    }

    @Test
    void moveTo_takesTheShorterAnticlockwiseWayAroundTheRing() {
        playerAtHomeWithMinutes(4320);

        // Rent Office is ring index 12: 12 steps clockwise but only 1 anticlockwise.
        MoveResult result = service.moveTo(Location.RENT_OFFICE);

        assertEquals(new MoveResult(MoveResult.Status.OK, 1, 160, 4160), result);
        verify(users).updatePosition(USER, 0, 1);
    }

    @Test
    void moveTo_crossTownIsSixStepsForFourHoursOfWalking() {
        playerAtHomeWithMinutes(4320);

        // Hi-Tech U (index 6) is the far side of the loop: 6 x 40 + 120 = 360 min.
        MoveResult result = service.moveTo(Location.HI_TECH_U);

        assertEquals(new MoveResult(MoveResult.Status.OK, 6, 360, 3960), result);
        verify(users).updatePosition(USER, 3, 3);
    }

    @Test
    void moveTo_theCurrentStopIsLegalAndChargesEntryOnly() {
        playerAtHomeWithMinutes(4320);

        MoveResult result = service.moveTo(Location.LOW_COST_HOUSING);

        assertEquals(new MoveResult(MoveResult.Status.OK, 0, 120, 4200), result);
        verify(users).updatePosition(USER, 0, 2);
    }

    @Test
    void moveTo_insufficientTimeRejectsAndPersistsNothing() {
        playerAtHomeWithMinutes(100); // 100 < 160

        MoveResult result = service.moveTo(Location.PAWN_SHOP);

        assertEquals(new MoveResult(MoveResult.Status.INSUFFICIENT_TIME, 1, 0, 100), result);
        verify(users, never()).updateTime(anyString(), anyInt());
        verify(users, never()).updatePosition(anyString(), anyInt(), anyInt());
    }

    @Test
    void moveTo_weekOverRejectsAndPersistsNothing() {
        playerAtHomeWithMinutes(0);

        MoveResult result = service.moveTo(Location.PAWN_SHOP);

        assertEquals(new MoveResult(MoveResult.Status.WEEK_OVER, 1, 0, 0), result);
        verify(users, never()).updateTime(anyString(), anyInt());
        verify(users, never()).updatePosition(anyString(), anyInt(), anyInt());
    }

    @Test
    void moveTo_landingExactlyOnZeroEndsTheWeekChargedButUnmoved() {
        playerAtHomeWithMinutes(160); // exactly one step + entry

        MoveResult result = service.moveTo(Location.PAWN_SHOP);

        assertEquals(new MoveResult(MoveResult.Status.WEEK_OVER, 1, 160, 0), result);
        verify(users).updateTime(USER, 0);
        verify(users, never()).updatePosition(anyString(), anyInt(), anyInt());
    }

    @Test
    void moveTo_aStaleSavedPositionMeasuresFromHome() {
        when(users.getXpos(USER)).thenReturn(2); // (2,2) is an inner cell, not a stop
        when(users.getYpos(USER)).thenReturn(2);
        when(users.getTime(USER)).thenReturn(4320);

        MoveResult result = service.moveTo(Location.PAWN_SHOP);

        assertEquals(new MoveResult(MoveResult.Status.OK, 1, 160, 4160), result);
        verify(users).updatePosition(USER, 0, 3);
    }

    // ---- currentLocation -----------------------------------------------------

    @Test
    void currentLocation_atAKnownStopReturnsThatStop() {
        when(users.getXpos(USER)).thenReturn(2); // Bank is (2,0)
        when(users.getYpos(USER)).thenReturn(0);

        assertEquals(Location.BANK, service.currentLocation());
    }

    @Test
    void currentLocation_aStaleSavedCellClampsToHome() {
        when(users.getXpos(USER)).thenReturn(2); // inner cell, not a stop
        when(users.getYpos(USER)).thenReturn(2);

        assertEquals(Location.LOW_COST_HOUSING, service.currentLocation());
    }
}
