package software.decibel.utils;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.PrivacySettings;
import software.decibel.dtos.user.SocialLinksDto;
import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.dtos.user.UserProfile;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.SocialLinks;
import software.decibel.entities.User;
import software.decibel.enums.SocialPlatform;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.SocialLinksRepository;

@Component
@RequiredArgsConstructor
public class UserMappingUtility {

    private final SocialLinksRepository socialLinksRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final LocationUtility locationUtility;

    // Builds the full UpdateProfileResponse for a user
    public UpdateProfileResponse toUpdateProfileResponse(User user, boolean includePrivacy, boolean isFollowed, boolean isFollowing, boolean isBlocked) {
        return new UpdateProfileResponse(
                toUserProfile(user, isFollowed, isFollowing, isBlocked),
                includePrivacy ? new PrivacySettings(user.isPrivate(), user.isShowHistory()) : null
        );
    }

    // Maps User entity to UserProfile DTO
    public UserProfile toUserProfile(User user, boolean isFollowed, boolean isFollowing, boolean isBlocked) {
        return new UserProfile(
                user.getId(),
                resolveEmail(user),
                user.getUsername(),
                user.getTier(),
                user.getFollowerCount(),
                user.getFollowingCount(),
                user.getTrackCount(),
                isFollowed,
                isFollowing,
                isBlocked,
                user.getBio(),
                locationUtility.parseCity(user.getLocation()),
                locationUtility.parseCountry(user.getLocation()),
                user.getAvatarUrl(),
                user.getCoverPhotoUrl(),
                user.getFavoriteGenres(),
                toSocialLinksDto(user)
        );
    }

    // Maps social links to flat SocialLinksDto
    public SocialLinksDto toSocialLinksDto(User user) {
        Map<SocialPlatform, String> linksMap = socialLinksRepository.findAllByUser(user)
                .stream()
                .collect(Collectors.toMap(SocialLinks::getPlatform, SocialLinks::getUrl));

        return new SocialLinksDto(
                linksMap.get(SocialPlatform.INSTAGRAM),
                linksMap.get(SocialPlatform.TWITTER),
                linksMap.get(SocialPlatform.WEBSITE)
        );
    }

    // Checks if any identity for the user has verified email
    public boolean isEmailVerified(User user) {
        return authIdentityRepository.findAllByUser(user)
                .stream()
                .anyMatch(AuthIdentity::isEmailVerified);
    }

    // Resolves email from any identity
    public String resolveEmail(User user) {
        return authIdentityRepository.findAllByUser(user)
                .stream()
                .findFirst()
                .map(AuthIdentity::getEmail)
                .orElse(null);
    }
}
