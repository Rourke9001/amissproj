package amiss.api.web.dto;

/** A successful rent payment: how much and how long it took, plus the fresh state. */
public record RentPaymentResponse(int amountPaid, int minutesCharged, PlayerStateDto state) {
}
