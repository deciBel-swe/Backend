package software.decibel.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.UpdateProfileRequest;
import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.dtos.user.UpdateUserImagesRequest;
import software.decibel.dtos.user.UpdateUserImagesResponse;
import software.decibel.dtos.user.UserProfileTokenResponse;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserProfileService;
import software.decibel.services.user.UserProfileTokenService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userService;
    private final UserProfileTokenService userProfileTokenService;

    //public, no auth required
    @GetMapping("/{userId}")
    public ResponseEntity<UpdateProfileResponse> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserPublicProfile(userId));
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
    public ResponseEntity<UpdateProfileResponse> getUserProfileByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserPublicProfileByUsername(username));
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
