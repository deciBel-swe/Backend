package software.decibel.dtos.user;

import software.decibel.enums.AccountTier;

public record TierUpgradeResponse(
        AccountTier tier,
        String message,
        String accessToken,
        String rawRefreshToken,
        long refreshTokenExpiresIn
        ) {

}
