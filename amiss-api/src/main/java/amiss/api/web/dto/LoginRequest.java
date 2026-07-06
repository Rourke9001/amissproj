package amiss.api.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/login}. */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
