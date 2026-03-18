package software.decibel.dtos.auth;

import jakarta.validation.constraints.NotBlank;

public record LogoutSessionRequest(
        @NotBlank
        String refreshToken
) {
}
