package software.decibel.utils;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.PrivacySettings;
import software.decibel.dtos.user.SocialLinksDto;
import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.dtos.user.UserProfile;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.User;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.SocialLinksRepository;

@Component
@RequiredArgsConstructor
public class UserMappingUtility {

    private final SocialLinksRepository socialLinksRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final LocationUtility locationUtility;

    // Builds the full UpdateProfileResponse for a user
    public UpdateProfileResponse toUpdateProfileResponse(User user, boolean includePrivacy, boolean emailVerified, boolean isFollowed, boolean isFollowing, boolean isBlocked) {
        return new UpdateProfileResponse(
                user.getId(),
                resolveEmail(user),
                user.getUsername(),
                emailVerified,
                user.getTier(),
                user.getFollowerCount(),
                user.getFollowingCount(),
                user.getTrackCount(),
                isFollowed,
                isFollowing,
                isBlocked,
                toUserProfile(user),
                toSocialLinksDto(user),
                includePrivacy ? new PrivacySettings(user.isPrivate(), user.isShowHistory()) : null
        );
    }

    // Maps User entity to UserProfile DTO
    public UserProfile toUserProfile(User user) {
        return new UserProfile(
                user.getBio(),
                locationUtility.parseCity(user.getLocation()),
                locationUtility.parseCountry(user.getLocation()),
                user.getAvatarUrl(),
                user.getCoverPhotoUrl(),
                user.getFavoriteGenres()
        );
    }

    // Maps social links list to flat SocialLinksDto
    public List<SocialLinksDto> toSocialLinksDto(User user) {
        return socialLinksRepository.findAllByUser(user)
                .stream()
                .map(s -> new SocialLinksDto(s.getPlatform(), s.getUrl()))
                .collect(Collectors.toList());
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
