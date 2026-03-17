package software.decibel.dtos.auth;

import software.decibel.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceInfo(
        @NotNull
        DeviceType deviceType,

        @NotBlank
        String fingerPrint,

        @NotBlank
        String deviceName
) {
}
