package software.decibel.dtos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RegisterLocalRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String username,

        @NotBlank
        String password,

        @NotNull
        LocalDate dateOfBirth,

        @NotBlank
        String gender,

        String city,
        String country,

        @NotBlank
        String captchaToken,

        @NotNull
        @Valid
        DeviceInfo deviceInfo
) {
}
