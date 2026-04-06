package software.decibel.dtos.user;

import java.util.List;

import software.decibel.enums.AccountTier;

public record UserProfile(
        Long id,
        String email,
        String username,
        String displayName,
        AccountTier tier,
        int followerCount,
        int followingCount,
        int trackCount,
        boolean isFollowed,
        boolean isFollowing,
        boolean isBlocked,
        String bio,
        String city,
        String country,
        String profilePic,
        String coverPic,
        List<String> favoriteGenres,
        List<SocialLinksDto> socialLinksDto) {

}
