package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.board.Location;
import amiss.domain.model.SaveState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TravelServiceTest {

    @Mock
    private SaveRepository saves;

    private TravelService service() {
        return new TravelService(saves, ActionCosts.defaults());
    }

    // TestSaves.newSave() starts at (0,0) = ring index 11 = Le Security Apartments.

    @Test
    void currentLocationReadsTheStartingStop() {
        SaveState save = TestSaves.newSave();
        assertEquals(Location.LE_SECURITY_APARTMENTS, service().currentLocation(save));
    }

    @Test
    void currentLocationOfAStaleCellIsHome() {
        SaveState save = TestSaves.newSave();
        save.setPos(3, 2); // the reserved timer cell: not a stop

        assertEquals(Location.LOW_COST_HOUSING, service().currentLocation(save));
    }

    @Test
    void movingToTheCurrentStopChargesEntryOnly() {
        SaveState save = TestSaves.newSave();

        MoveResult result = service().moveTo(save, Location.LE_SECURITY_APARTMENTS);

        assertEquals(MoveResult.Status.OK, result.status());
        assertEquals(0, result.steps());
        assertEquals(120, result.minutesCharged());       // enter-building only
        assertEquals(4200, result.remainingMinutes());
        assertEquals(4200, save.timeMinutes());
        assertEquals(0, save.xpos());
        assertEquals(0, save.ypos());
        verify(saves).update(save);
    }

    @Test
    void movingChargesStepsPlusEntryAndPersistsPosition() {
        SaveState save = TestSaves.newSave();

        // Le Security Apartments (11) -> Low-Cost Housing (0): ring distance 2.
        MoveResult result = service().moveTo(save, Location.LOW_COST_HOUSING);

        assertEquals(MoveResult.Status.OK, result.status());
        assertEquals(2, result.steps());
        assertEquals(200, result.minutesCharged());        // 2*40 + 120
        assertEquals(4120, result.remainingMinutes());
        assertEquals(4120, save.timeMinutes());
        assertEquals(0, save.xpos());
        assertEquals(2, save.ypos());
        verify(saves).update(save);
    }

    @Test
    void aStaleSavedCellMeasuresFromHome() {
        SaveState save = TestSaves.newSave();
        save.setPos(3, 2); // stale/invalid cell

        // Home (0) -> Z-Mart (2): ring distance 2.
        MoveResult result = service().moveTo(save, Location.Z_MART);

        assertEquals(2, result.steps());
        assertEquals(200, result.minutesCharged());
        assertEquals(MoveResult.Status.OK, result.status());
    }

    @Test
    void landingExactlyOnZeroChargesButEndsTheWeekWithoutMoving() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(200); // exactly the cost of the 2-step move below

        MoveResult result = service().moveTo(save, Location.LOW_COST_HOUSING);

        assertEquals(MoveResult.Status.WEEK_OVER, result.status());
        assertEquals(200, result.minutesCharged());
        assertEquals(0, result.remainingMinutes());
        assertEquals(0, save.timeMinutes());
        // position unchanged - the coming rollover resets it to home anyway
        assertEquals(0, save.xpos());
        assertEquals(0, save.ypos());
        verify(saves).update(save);
    }

    @Test
    void insufficientTimeRejectsAndPersistsNothing() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(100); // < 200 required

        MoveResult result = service().moveTo(save, Location.LOW_COST_HOUSING);

        assertEquals(MoveResult.Status.INSUFFICIENT_TIME, result.status());
        assertEquals(0, result.minutesCharged());
        assertEquals(100, result.remainingMinutes());
        assertEquals(100, save.timeMinutes());
        assertEquals(0, save.xpos());
        assertEquals(0, save.ypos());
        verifyNoInteractions(saves);
    }

    @Test
    void attemptingToMoveWhenTheWeekIsAlreadyOverReportsWeekOver() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(0);

        MoveResult result = service().moveTo(save, Location.LOW_COST_HOUSING);

        assertEquals(MoveResult.Status.WEEK_OVER, result.status());
        assertEquals(0, result.minutesCharged());
        assertEquals(0, result.remainingMinutes());
        verify(saves, never()).update(save);
    }
}
