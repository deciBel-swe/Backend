package software.decibel.controllers.User;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.dtos.user.UpdateProfileRequest;
import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.dtos.user.UpdateUserImagesRequest;
import software.decibel.dtos.user.UpdateUserImagesResponse;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.dtos.user.UserProfileTokenResponse;
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserProfileService;
import software.decibel.services.user.UserProfileTokenService;
import software.decibel.services.user.UserSuggestionService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userService;
    private final UserProfileTokenService userProfileTokenService;
    private final UserSuggestionService userSuggestionService;
    private final UserRepository userRepository;

    // function to get suggested users based on interests
    @GetMapping("/suggested")
    public ResponseEntity<List<UserFollowDto>> getSuggestedUsers(
            @RequestParam(defaultValue = "5") int limit
    ) {
        Long currentUserId = JwtService.getCurrentUserId();
        // get the user object from repository and check if logged in
        User currentUser = (currentUserId != null) ? userRepository.getReferenceById(currentUserId) : null;

        // fetch suggestions through service
        List<UserFollowDto> suggestions = userSuggestionService.getSuggestedUsers(currentUser, limit);
        return ResponseEntity.ok(suggestions);
    }

    //public, no auth required
    @GetMapping("/{userId}")
    public ResponseEntity<UpdateProfileResponse> getUserProfile(@PathVariable Long userId, @AuthenticationPrincipal UserPrincipal principal) {
        Long currentUserId = (principal != null) ? principal.getId() : null;
        return ResponseEntity.ok(userService.getUserPublicProfile(userId, currentUserId));
    }

    //full profile with privacy settings
    @GetMapping("/me")
    public ResponseEntity<UpdateProfileResponse> getMyProfile() {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(userService.getMyProfile(currentUserId));
    }

    //partial profile update
    @PatchMapping("/me")
    public ResponseEntity<UpdateProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(userService.updateMyProfile(currentUserId, request));
    }

    //delete profile picture
    @DeleteMapping("/me/images/avatar")
    public ResponseEntity<Void> deleteMyAvatar() {
        Long currentUserId = JwtService.getCurrentUserId();
        userService.deleteMyAvatar(currentUserId);
        return ResponseEntity.noContent().build();
    }

    //delete cover photo
    @DeleteMapping("/me/images/cover")
    public ResponseEntity<Void> deleteMyCoverPhoto() {
        Long currentUserId = JwtService.getCurrentUserId();
        userService.deleteMyCoverPhoto(currentUserId);
        return ResponseEntity.noContent().build();
    }

    //update profile/cover pictures
    @PatchMapping(value = "/me/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UpdateUserImagesResponse> updateMyImages(
            @Valid @ModelAttribute UpdateUserImagesRequest request
    ) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(userService.updateMyImages(currentUserId, request.profilePic(), request.coverPic()));
    }

    // GET /users/username/{username} — public, no auth required
    @GetMapping("/username/{username}")
    public ResponseEntity<UpdateProfileResponse> getUserProfileByUsername(@PathVariable String username, @AuthenticationPrincipal UserPrincipal principal) {
        Long currentUserId = (principal != null) ? principal.getId() : null;
        return ResponseEntity.ok(userService.getUserPublicProfileByUsername(username, currentUserId));
    }

    //GET /users/me/secret-link — get active profile token
    @GetMapping("/me/secret-link")
    public ResponseEntity<UserProfileTokenResponse> getSecretLink() {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(userProfileTokenService.getActiveToken(currentUserId));
    }

    // POST /users/me/secret-link/regenerate — regenerate profile token
    @PostMapping("/me/secret-link/regenerate")
    public ResponseEntity<UserProfileTokenResponse> regenerateSecretLink() {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(userProfileTokenService.regenerateToken(currentUserId));
    }

    // GET /users/profile/token/{token} — public, get profile by secret token
    @GetMapping("/profile/token/{token}")
    public ResponseEntity<UpdateProfileResponse> getUserProfileByToken(@PathVariable String token) {
        return ResponseEntity.ok(userService.getUserPublicProfileByToken(token));
    }

}
