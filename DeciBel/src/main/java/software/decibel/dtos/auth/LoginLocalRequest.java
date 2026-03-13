package software.decibel.dtos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginLocalRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String password,

        @NotNull
        @Valid
        DeviceInfo deviceInfo
) {
}
