package amiss.application.service.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import amiss.application.config.ActionCosts;
import amiss.application.port.SaveRepository;
import amiss.domain.model.SaveState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelaxServiceTest {

    @Mock
    private SaveRepository saves;

    private RelaxService service() {
        return new RelaxService(saves, ActionCosts.defaults());
    }

    @Test
    void firstRelaxThisTurnGrantsTheStatAndHappinessBonus() {
        SaveState save = TestSaves.newSave();   // relaxation 10, happiness 50, time 3600

        RelaxService.RelaxOutcome outcome = service().relax(save);

        assertEquals(RelaxService.RelaxOutcome.Status.OK, outcome.status());
        assertEquals(13, save.relaxation());
        assertEquals(52, save.happiness());
        assertEquals(3600 - ActionCosts.defaults().relaxMinutes(), save.timeMinutes());
        verify(saves).update(save);
    }

    @Test
    void secondRelaxSameTurnRaisesTheStatButGrantsNoMoreHappiness() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(3600);
        service().relax(save);
        int happinessAfterFirst = save.happiness();

        service().relax(save);

        assertEquals(16, save.relaxation());
        assertEquals(happinessAfterFirst, save.happiness());
    }

    @Test
    void relaxationClampsAtFifty() {
        SaveState save = TestSaves.newSave();
        save.setRelaxation(49);

        service().relax(save);

        assertEquals(50, save.relaxation());
    }

    @Test
    void weekOverRejectsTheAction() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(0);

        RelaxService.RelaxOutcome outcome = service().relax(save);

        assertEquals(RelaxService.RelaxOutcome.Status.WEEK_OVER, outcome.status());
        assertEquals(10, save.relaxation());
    }

    @Test
    void insufficientTimeRejectsTheAction() {
        SaveState save = TestSaves.newSave();
        save.setTimeMinutes(100);   // less than the 360-minute relax cost

        RelaxService.RelaxOutcome outcome = service().relax(save);

        assertEquals(RelaxService.RelaxOutcome.Status.INSUFFICIENT_TIME, outcome.status());
        assertEquals(10, save.relaxation());
    }
}
