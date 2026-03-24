package software.decibel.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefreshTokenResponse(
        @NotBlank
        String accessToken,

        @NotNull
        Long expiresIn
) {
}
