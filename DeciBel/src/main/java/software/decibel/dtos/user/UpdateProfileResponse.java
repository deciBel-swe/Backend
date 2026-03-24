package software.decibel.dtos.user;

import software.decibel.enums.AccountTier;

public record UpdateProfileResponse(
        Long id,
        String email,
        String username,
        boolean emailVerified,
        AccountTier tier,
        int followerCount,
        int followingCount,
        int trackCount,
        UserProfile profile,
        SocialLinksDto socialLinks,
        PrivacySettings privacySettings
        ) {

}
