package software.decibel.dtos.auth;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import software.decibel.customValidation.ValidEmail;

public record RegisterLocalRequest(
        @NotBlank
        @ValidEmail
        String email,
        @NotBlank
        String displayName,
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
