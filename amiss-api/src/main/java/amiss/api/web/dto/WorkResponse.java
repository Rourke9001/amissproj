package amiss.api.web.dto;

/** A completed work shift: wages paid and whether an outstanding debt docked R10. */
public record WorkResponse(String job, int hourlyWage, int minutesCharged, boolean debtDocked, PlayerStateDto state) {
}
