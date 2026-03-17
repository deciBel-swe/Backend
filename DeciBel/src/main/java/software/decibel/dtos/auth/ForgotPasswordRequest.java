package software.decibel.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Data Transfer Object for forgot password requests.

public record ForgotPasswordRequest(
        @Email
        @NotBlank
        String email
)
{ }
