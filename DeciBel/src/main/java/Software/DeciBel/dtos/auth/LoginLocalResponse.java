package software.decibel.dtos.auth;

import software.decibel.enums.AccountTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginLocalResponse(
        @NotBlank
        String accessToken,

        @NotBlank
        String refreshToken,

        @NotNull
        UserInfo user
) {
    public record UserInfo(
            @NotNull
            Long id,

            @NotBlank
            String username,

            @NotNull
            AccountTier tier,

            String profileUrl,
            String avatarUrl
    ) {
    }
}
