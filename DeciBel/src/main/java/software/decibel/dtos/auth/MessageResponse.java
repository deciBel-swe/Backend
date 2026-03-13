package software.decibel.dtos.auth;

import jakarta.validation.constraints.NotBlank;

public record MessageResponse(
        @NotBlank
        String message
) {
}
