package software.decibel.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.UserPublicProfileDto;
import software.decibel.dtos.user.UserPrivateProfileDto;
import software.decibel.services.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import software.decibel.services.JwtService;


@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;

    //get another user's profile
    @GetMapping("/{userId}")
    public ResponseEntity<UserPublicProfileDto> getUserProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserPublicProfile(userId));
    }
    //gets own profile contains extra
    @GetMapping("/me")
    public ResponseEntity<UserPrivateProfileDto> getMyProfile() {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(userService.getUserPrivateProfile(currentUserId));
    }
    
    
}
