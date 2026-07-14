package amiss.api.web.dto;

import amiss.application.service.save.EconomyEvent;

/**
 * The economy's move at rollover, for the end-week report (KAN-48). {@code event}
 * NONE/BOOM/CRASH; {@code severity} only for crashes. Index/Reading themselves stay
 * hidden — this reports consequences, never the numbers.
 */
public record EconomyEventDto(String event, String severity, boolean fired,
        Integer wageCutTo, boolean bankWiped, int happinessLost) {

    public static EconomyEventDto from(EconomyEvent e) {
        return new EconomyEventDto(e.type().name(),
                e.severity() == null ? null : e.severity().name(),
                e.fired(), e.wageCutTo(), e.bankWiped(), e.happinessLost());
    }
}
