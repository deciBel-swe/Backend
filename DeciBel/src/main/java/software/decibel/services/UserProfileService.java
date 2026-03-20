package software.decibel.services;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.PrivacySettings;
import software.decibel.dtos.user.SocialLinksDto;
import software.decibel.dtos.user.UpdateProfileRequest;
import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.dtos.user.UserProfile;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.SocialLinks;
import software.decibel.entities.User;
import software.decibel.enums.SocialPlatform;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.SocialLinksRepository;
import software.decibel.repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final SocialLinksRepository socialLinksRepository;
    private final AuthIdentityRepository authIdentityRepository;

    // Public profile — no auth required
    @Transactional(readOnly = true)
    public UpdateProfileResponse getUserPublicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        return buildUpdateProfileResponse(user, false, false);
    }

    // Private profile — authenticated, includes privacy settings and email verified
    @Transactional(readOnly = true)
    public UpdateProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        return buildUpdateProfileResponse(user, true, isEmailVerified(user));
    }

    // Update profile — authenticated, partial update
    @Transactional
    public UpdateProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        if (request.bio() != null) {
            user.setBio(request.bio());
        }

        if (request.city() != null || request.country() != null) {
            String city = request.city() != null ? request.city() : parseCity(user.getLocation());
            String country = request.country() != null ? request.country() : parseCountry(user.getLocation());
            user.setLocation(buildLocation(city, country));
        }

        if (request.favoriteGenres() != null) {
            user.setFavoriteGenres(request.favoriteGenres());
        }

        userRepository.save(user);

        if (request.socialLinks() != null) {
            SocialPlatform platform = request.socialLinks().getPlatform();
            String url = request.socialLinks().getUrl();
            SocialLinks socialLink = socialLinksRepository
                    .findByUserAndPlatform(user, platform)
                    .orElse(SocialLinks.builder().user(user).platform(platform).build());
            socialLink.setUrl(url);
            socialLinksRepository.save(socialLink);
        }

        UpdateProfileResponse updated = getMyProfile(userId);
        return new UpdateProfileResponse(
                updated.id(),
                updated.email(),
                updated.username(),
                updated.emailVerified(),
                updated.tier(),
                updated.followerCount(),
                updated.followingCount(),
                updated.trackCount(),
                updated.profile(),
                updated.socialLinksDto(),
                updated.privacySettings()
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private UpdateProfileResponse buildUpdateProfileResponse(User user, boolean includePrivacy, boolean emailVerified) {
        // Map social links list into a flat object
        Map<SocialPlatform, String> linksMap = socialLinksRepository.findAllByUser(user)
                .stream()
                .collect(Collectors.toMap(SocialLinks::getPlatform, SocialLinks::getUrl));

        SocialLinksDto socialLinksDto = new SocialLinksDto(
                linksMap.get(SocialPlatform.INSTAGRAM),
                linksMap.get(SocialPlatform.TWITTER),
                linksMap.get(SocialPlatform.WEBSITE),
                linksMap.get(SocialPlatform.SUPPORT_LINK)
        );

        UserProfile profileDto = new UserProfile(
                user.getBio(),
                parseCity(user.getLocation()),
                parseCountry(user.getLocation()),
                user.getAvatarUrl(),
                user.getCoverPhotoUrl(),
                user.getFavoriteGenres()
        );

        PrivacySettings privacySettingsDto = includePrivacy
                ? new PrivacySettings(user.isPrivate(), user.isShowHistory())
                : null;

        // Resolve email from any identity
        String email = authIdentityRepository.findAllByUser(user)
                .stream()
                .findFirst()
                .map(AuthIdentity::getEmail)
                .orElse(null);

        return new UpdateProfileResponse(
                user.getId(),
                email,
                user.getUsername(),
                emailVerified,
                user.getTier(),
                user.getFollowerCount(),
                user.getFollowingCount(),
                user.getTrackCount(),
                profileDto,
                socialLinksDto,
                privacySettingsDto
        );
    }

    private boolean isEmailVerified(User user) {
        return authIdentityRepository.findAllByUser(user)
                .stream()
                .anyMatch(AuthIdentity::isEmailVerified);
    }

    private String parseCity(String location) {
        if (location == null) {
            return null;
        }
        String[] parts = location.split(",", 2);
        return parts[0].trim();
    }

    private String parseCountry(String location) {
        if (location == null) {
            return null;
        }
        String[] parts = location.split(",", 2);
        return parts.length > 1 ? parts[1].trim() : null;
    }

    private String buildLocation(String city, String country) {
        if (city == null && country == null) {
            return null;
        }
        if (city == null) {
            return country;
        }
        if (country == null) {
            return city;
        }
        return city + ", " + country;
    }
}
