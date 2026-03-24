package software.decibel.dtos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GoogleOauthRequest(
        @NotBlank
        String authTokenDto,

        @NotNull
        @Valid
        DeviceInfo deviceInfo
) {
}
