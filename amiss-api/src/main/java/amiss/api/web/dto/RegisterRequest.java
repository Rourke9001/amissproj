package amiss.api.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/register}. */
public record RegisterRequest(@NotBlank String username, @NotBlank String password) {
}
