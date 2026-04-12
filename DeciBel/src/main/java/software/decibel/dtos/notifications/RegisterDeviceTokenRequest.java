package software.decibel.dtos.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import software.decibel.enums.DeviceType;

public record RegisterDeviceTokenRequest(
        @NotBlank(message = "Device token is required")
        String token,
        @NotNull(message = "deviceType must be one of: DESKTOP, MOBILE, TABLET")
        DeviceType deviceType) {

}
