package software.decibel.dtos.notifications;

import jakarta.validation.constraints.NotBlank;

public record RegisterFcmTokenRequest(
        @NotBlank(message = "FCM token must not be blank")
        String token,
        String deviceName
        ) {

}
