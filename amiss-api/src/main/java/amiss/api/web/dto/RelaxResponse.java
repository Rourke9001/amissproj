package amiss.api.web.dto;

/** A successful Relax action. */
public record RelaxResponse(int minutesCharged, int relaxation, SaveStateDto state) {
}
