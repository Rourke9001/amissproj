package amiss.api.web.dto;

/** Body of {@code POST .../bank/deposit} and {@code .../bank/withdraw}. */
public record BankRequest(Integer amount) {
}
