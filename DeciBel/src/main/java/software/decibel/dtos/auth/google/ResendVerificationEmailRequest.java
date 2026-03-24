package software.decibel.dtos.auth.google;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import software.decibel.dtos.auth.DeviceInfo;

public record ResendVerificationEmailRequest(
        @NotBlank(message = "Email must not be blank")
        String email,
        @NotNull(message = "Device info must not be null")
        @Valid
        DeviceInfo deviceInfo) {

}
