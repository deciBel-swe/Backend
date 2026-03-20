package software.decibel.services;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import software.decibel.dtos.user.SocialLinksDto;
import software.decibel.dtos.user.UserPublicProfileDto;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.SocialLinksRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.dtos.user.UserPrivateProfileDto;

import java.util.List;

import software.decibel.entities.AuthIdentity;
import software.decibel.repositories.AuthIdentityRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SocialLinksRepository socialLinksRepository;
    private final AuthIdentityRepository authIdentityRepository;

    //public profile service method, does not include private info
    @Transactional(readOnly = true)
    public UserPublicProfileDto getUserPublicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        // Split location string back into city and country
        String city = null;
        String country = null;
        if (user.getLocation() != null) {
            String[] parts = user.getLocation().split(",", 2);
            city = parts[0].trim();
            country = parts.length > 1 ? parts[1].trim() : null;
        }
        //get social links and convert to DTOs
        List<SocialLinksDto> socialLinks = socialLinksRepository.findAllByUser(user)
                .stream()
                .map(s -> new SocialLinksDto(s.getPlatform(), s.getUrl()))
                .toList();
        //return the public profile DTO
        return new UserPublicProfileDto(
                user.getId(),
                user.getUsername(),
                user.getTier(),
                user.getBio(),
                city,
                country,
                user.getAvatarUrl(),
                user.getCoverPhotoUrl(),
                user.getFavoriteGenres(),
                socialLinks.isEmpty() ? null : socialLinks,
                user.getFollowerCount(),
                user.getFollowingCount(),
                user.getTrackCount()
        );
    }

    //private profile service method, includes private info
    @Transactional(readOnly = true)
    public UserPrivateProfileDto getUserPrivateProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));
        UserPublicProfileDto publicProfile = getUserPublicProfile(userId);

        Boolean emailVerified = authIdentityRepository.findAllByUser(user)
                .stream()
                .anyMatch(AuthIdentity::isEmailVerified);

        return new UserPrivateProfileDto(
                publicProfile,
                user.isPrivate(),
                user.isShowHistory(),
                emailVerified
        );
    }

}
