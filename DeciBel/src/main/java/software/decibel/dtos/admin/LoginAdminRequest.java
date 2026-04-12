package software.decibel.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import software.decibel.dtos.auth.DeviceInfo;

public record LoginAdminRequest(

    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Password is required")
    String password,

    @NotNull(message = "Device Info is required")
    DeviceInfo deviceInfo
) {}
