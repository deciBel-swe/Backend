package software.decibel.dtos.user;

import software.decibel.enums.AccountTier;
import java.util.List;
import software.decibel.dtos.user.SocialLinksDto;

public record UserPublicProfileDto(
        Long id,
        String username,
        AccountTier tier,
        String bio,
        String city,
        String country,
        String avatarUrl,
        String coverPhotoUrl,
        List<String> favoriteGenres,
        List<SocialLinksDto> socialLinks,
        int followerCount,
        int followingCount,
        int trackCount
) {
}
