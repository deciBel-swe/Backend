package software.decibel.dtos.user;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailChangeRequest(
        @NotBlank
        String token
) {
}
