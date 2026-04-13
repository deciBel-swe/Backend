package software.decibel.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import software.decibel.enums.AccountTier;

public record LoginLocalResponse(
        @NotBlank
        String accessToken,
        @NotNull
        Long expiresIn,
        @NotNull
        UserInfo user
        ) {

    public record UserInfo(
            @NotNull
            Long id,
            @NotBlank
            String username,
            @NotNull
            String displayName,
            @NotNull
            AccountTier tier,
            String profileUrl,
            String avatarUrl,
            @NotNull
            boolean isNewUser
            ) {

    }
}
