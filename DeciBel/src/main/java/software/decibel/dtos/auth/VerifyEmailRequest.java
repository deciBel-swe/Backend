package software.decibel.dtos.auth;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank
        String token
) {
}
