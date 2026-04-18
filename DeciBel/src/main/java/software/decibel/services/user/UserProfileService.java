package software.decibel.services.user;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.UpdateProfileRequest;
import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.dtos.user.UpdateUserImagesResponse;
import software.decibel.entities.SocialLinks;
import software.decibel.entities.User;
import software.decibel.entities.UserProfileToken;
import software.decibel.enums.FileType;
import software.decibel.enums.SocialPlatform;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.SocialLinksRepository;
import software.decibel.repositories.UserProfileTokenRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserService;
import software.decibel.utils.FileUtilityAzure;
import software.decibel.utils.LocationUtility;
import software.decibel.utils.UserMappingUtility;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final SocialLinksRepository socialLinksRepository;
    private final FileUtilityAzure fileUtilityAzure;
    private final LocationUtility locationUtility;
    private final UserMappingUtility userMappingUtility;
    private final UserProfileTokenRepository userProfileTokenRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final UserService userService;

    // Public profile — no auth required
    @Transactional(readOnly = true)
    public UpdateProfileResponse getUserPublicProfile(Long userId, Long currentUserId) {
        User user = findUserById(userId);
        //If the profile is private AND the current user is not the owner, throw a 404
        if (user.isPrivate() && !Objects.equals(user.getId(), currentUserId)) {
            throw new ResourceNotFoundException("User with ID " + userId + " not found");
        }
        //check if the current user is blocked by this profile, if so, throw a 404
        if (currentUserId != null && blockRepository.existsByBlockerAndBlocked(user, userRepository.getReferenceById(currentUserId))) {
            throw new ResourceNotFoundException("User with ID " + userId + " not found");
        }
        return getResponseWithFollowStatus(user, false);
    }

    // Private profile — authenticated, includes privacy settings and email verified
    @Transactional(readOnly = true)
    public UpdateProfileResponse getMyProfile(Long userId) {
        User user = findUserById(userId);
        return getResponseWithFollowStatus(user, true);
    }

    // Update profile — authenticated, partial update
    @Transactional
    public UpdateProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserById(userId);

        //checks Bio 
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        //checks City and Country, if either is provided, we need to update the location string
        if (request.city() != null || request.country() != null) {
            String city = request.city() != null ? request.city() : locationUtility.parseCity(user.getLocation());
            String country = request.country() != null ? request.country() : locationUtility.parseCountry(user.getLocation());
            user.setLocation(locationUtility.buildLocation(city, country));
        }
        //checks Favorite Genres

        if (request.favoriteGenres() != null) {
            user.setFavoriteGenres(request.favoriteGenres());
        }

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        userRepository.save(user);

        if (request.socialLinks() != null) {
            upsertSocialLink(user, SocialPlatform.INSTAGRAM, request.socialLinks().instagram());
            upsertSocialLink(user, SocialPlatform.TWITTER, request.socialLinks().twitter());
            upsertSocialLink(user, SocialPlatform.WEBSITE, request.socialLinks().website());
        }
        //load updated user
        User updatedUser = findUserById(userId);
        return getResponseWithFollowStatus(updatedUser, true);
    }

    @Transactional(readOnly = true)
    public UpdateProfileResponse getUserPublicProfileByUsername(String username, Long currentUserId) {
        User user = userService.getUserIfExistsByUsername(username);
        //If the profile is private AND the current user is not the owner, throw a 404
        if (user.isPrivate() && !Objects.equals(user.getId(), currentUserId)) {
            throw new ResourceNotFoundException("User with username " + username + " not found");
        }
        //check if the current user is blocked by this profile, if so, throw a 404
        if (currentUserId != null && blockRepository.existsByBlockerAndBlocked(user, userRepository.getReferenceById(currentUserId))) {
            throw new ResourceNotFoundException("User with username " + username + " not found");
        }
        return getResponseWithFollowStatus(user, false);
    }

    // Update profile/cover images — authenticated
    @Transactional
    public UpdateUserImagesResponse updateMyImages(Long userId, MultipartFile profilePic, MultipartFile coverPic) {
        User user = findUserById(userId);

        if (profilePic != null && !profilePic.isEmpty()) {
            user.setAvatarUrl(fileUtilityAzure.saveFile(profilePic, FileType.AVATARS));
        }

        if (coverPic != null && !coverPic.isEmpty()) {
            user.setCoverPhotoUrl(fileUtilityAzure.saveFile(coverPic, FileType.PROFILE_COVERS));
        }

        userRepository.save(user);
        return new UpdateUserImagesResponse(user.getAvatarUrl(), user.getCoverPhotoUrl());
    }

    // Delete profile picture
    @Transactional
    public void deleteMyAvatar(Long userId) {
        User user = findUserById(userId);
        if (user.getAvatarUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(user.getAvatarUrl());
            user.setAvatarUrl(null);
            userRepository.save(user);
        }
    }

    // Delete cover photo
    @Transactional
    public void deleteMyCoverPhoto(Long userId) {
        User user = findUserById(userId);
        if (user.getCoverPhotoUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(user.getCoverPhotoUrl());
            user.setCoverPhotoUrl(null);
            userRepository.save(user);
        }
    }

    //used for getting the userProfile by token
    @Transactional(readOnly = true)
    public UpdateProfileResponse getUserPublicProfileByToken(String token) {
        UserProfileToken profileToken = userProfileTokenRepository
                .findByTokenAndIsDeletedFalse(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired profile token"));

        return getResponseWithFollowStatus(profileToken.getUser(), false);
    }

    private UpdateProfileResponse getResponseWithFollowStatus(User profileUser, boolean includePrivacy) {
        boolean isFollowed = false;
        boolean isFollowing = false;
        boolean isBlocked = false;

        try {
            Long currentUserId = JwtService.getCurrentUserId();
            if (currentUserId != null && !currentUserId.equals(profileUser.getId())) {
                User currentUser = userRepository.getReferenceById(currentUserId);
                isFollowed = followRepository.existsByFollowerAndFollowing(currentUser, profileUser);
                isFollowing = followRepository.existsByFollowerAndFollowing(profileUser, currentUser);
                isBlocked = blockRepository.existsByBlockerAndBlocked(currentUser, profileUser);
            }
        } catch (Exception ignored) {
            // No authenticated user or other security context issue
        }

        return userMappingUtility.toUpdateProfileResponse(profileUser, includePrivacy, isFollowed, isFollowing, isBlocked);
    }

    private User findUserById(Long userId) {
        return userService.getUserIfExistsById(userId);
    }

    private void upsertSocialLink(User user, SocialPlatform platform, String url) {
        // Safety check: Don't do anything if the URL is missing or blank
        if (url == null || url.isBlank()) {
            return;
        }

        // Find the existing link or create a new one
        SocialLinks link = socialLinksRepository
                .findByUserAndPlatform(user, platform)
                .orElse(SocialLinks.builder().user(user).platform(platform).build());

        // Update the URL and save
        link.setUrl(url);
        socialLinksRepository.save(link);
    }
}
