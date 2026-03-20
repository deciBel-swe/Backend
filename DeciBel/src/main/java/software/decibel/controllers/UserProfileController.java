package software.decibel.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.UpdateProfileRequest;
import software.decibel.dtos.user.UpdateProfileResponse;
import software.decibel.services.JwtService;
import software.decibel.services.UserProfileService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userService;

    // GET /users/{userId} — public, no auth required
    @GetMapping("/{userId}")
    public ResponseEntity<UpdateProfileResponse> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserPublicProfile(userId));
    }

    // GET /users/me — authenticated, full profile with privacy settings
    @GetMapping("/me")
    public ResponseEntity<UpdateProfileResponse> getMyProfile() {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(userService.getMyProfile(currentUserId));
    }

    // PATCH /users/me — authenticated, partial profile update
    @PatchMapping("/me")
    public ResponseEntity<UpdateProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(userService.updateMyProfile(currentUserId, request));
    }

}
