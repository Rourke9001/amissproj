package amiss.api.web.dto;

import amiss.application.service.save.DoctorVisitOutcome;

/** Whether a Doctor Visit fired at this rollover, and what it cost (KAN-23). */
public record DoctorVisitDto(boolean triggered, int hoursLost, int happinessLost, int cashLost) {

    public static DoctorVisitDto from(DoctorVisitOutcome outcome) {
        return new DoctorVisitDto(outcome.triggered(), outcome.minutesLost() / 60,
                outcome.happinessLost(), outcome.cashLost());
    }
}
