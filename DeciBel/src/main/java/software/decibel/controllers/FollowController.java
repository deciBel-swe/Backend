package software.decibel.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.engagement.FollowResponseDto;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.services.FollowService;
import software.decibel.services.JwtService;

// Controller for follow/unfollow and retrieving follow lists
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    // POST: Follow a user
    @PostMapping("/{userId}/follow")
    public ResponseEntity<FollowResponseDto> followUser(@PathVariable Long userId) {
        Long currentUserId = JwtService.getCurrentUserId();
        followService.followUser(currentUserId, userId);
        return ResponseEntity.ok(new FollowResponseDto("User followed successfully", true));
    }

    // DELETE: Unfollow a user
    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<FollowResponseDto> unfollowUser(@PathVariable Long userId) {
        Long currentUserId = JwtService.getCurrentUserId();
        followService.unfollowUser(currentUserId, userId);
        return ResponseEntity.noContent().build();
    }

    // GET: Get list of followers for a user
    @GetMapping("/{userId}/followers")
    public ResponseEntity<Page<UserFollowDto>> getFollowers(
            @PathVariable Long userId,
            Pageable pageable) {
        Long currentUserId = null;
        try {
            // authentication to show follow status
            currentUserId = JwtService.getCurrentUserId();
        } catch (Exception e) {
            // Anonymous access allowed
        }
        return ResponseEntity.ok(followService.getFollowers(userId, currentUserId, pageable));
    }

    // GET: Get list of users followed by a user
    @GetMapping("/{userId}/following")
    public ResponseEntity<Page<UserFollowDto>> getFollowing(
            @PathVariable Long userId,
            Pageable pageable) {
        Long currentUserId = null;
        try {
            // Optional authentication to show follow status
            currentUserId = JwtService.getCurrentUserId();
        } catch (Exception e) {
            // Anonymous access allowed
        }
        return ResponseEntity.ok(followService.getFollowing(userId, currentUserId, pageable));
    }
}
