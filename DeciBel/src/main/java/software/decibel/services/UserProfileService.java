package software.decibel.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.UpdateProfileRequest;
import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.dtos.user.UpdateUserImagesResponse;
import software.decibel.entities.SocialLinks;
import software.decibel.entities.User;
import software.decibel.enums.FileType;
import software.decibel.enums.SocialPlatform;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.SocialLinksRepository;
import software.decibel.repositories.UserRepository;
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

    // Public profile — no auth required
    @Transactional(readOnly = true)
    public UpdateProfileResponse getUserPublicProfile(Long userId) {
        User user = findUserById(userId);
        return userMappingUtility.toUpdateProfileResponse(user, false, false);
    }

    // Private profile — authenticated, includes privacy settings and email verified
    @Transactional(readOnly = true)
    public UpdateProfileResponse getMyProfile(Long userId) {
        User user = findUserById(userId);
        return userMappingUtility.toUpdateProfileResponse(user, true, userMappingUtility.isEmailVerified(user));
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

        userRepository.save(user);

        if (request.socialLinksDto() != null) {
            SocialPlatform platform = request.socialLinksDto().platform();
            String url = request.socialLinksDto().url();
            SocialLinks socialLink = socialLinksRepository
                    .findByUserAndPlatform(user, platform)
                    .orElse(SocialLinks.builder().user(user).platform(platform).build());
            socialLink.setUrl(url);
            socialLinksRepository.save(socialLink);
        }
        //load updated user
        User updatedUser = findUserById(userId);
        return userMappingUtility.toUpdateProfileResponse(updatedUser, true, userMappingUtility.isEmailVerified(updatedUser));
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

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));
    }
}
