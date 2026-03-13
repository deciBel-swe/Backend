package software.decibel.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ForgotPasswordRequest(
        @Email
        @NotBlank
        String email
)
{ }
