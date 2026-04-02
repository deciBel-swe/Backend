package software.decibel.dtos.user;

import java.util.List;

import software.decibel.enums.AccountTier;

public record UpdateProfileResponse(
        Long id,
        String email,
        String username,
        AccountTier tier,
        int followerCount,
        int followingCount,
        int trackCount,
        boolean isFollowed,
        boolean isFollowing,
        boolean isBlocked,
        UserProfile profile,
        List<SocialLinksDto> socialLinksDto,
        PrivacySettings privacySettings) {

}
