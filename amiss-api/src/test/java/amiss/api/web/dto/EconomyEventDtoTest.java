package amiss.api.web.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import amiss.application.service.save.EconomyEvent;
import org.junit.jupiter.api.Test;

/** {@link EconomyEventDto#from} on the wire-shape edges: BOOM never carries a severity. */
class EconomyEventDtoTest {

    @Test
    void fromMapsABoomEventWithNoSeverity() {
        EconomyEvent boom = new EconomyEvent(EconomyEvent.Type.BOOM, null, false, null, false, 0);

        EconomyEventDto dto = EconomyEventDto.from(boom);

        assertEquals("BOOM", dto.event());
        assertNull(dto.severity());
    }
}
