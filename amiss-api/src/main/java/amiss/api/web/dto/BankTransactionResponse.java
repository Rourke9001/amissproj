package amiss.api.web.dto;

/** A successful bank transfer: which operation, how much, and the fresh state. */
public record BankTransactionResponse(String operation, int amount, SaveStateDto state) {
}
