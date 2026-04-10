package software.decibel.services;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.entities.Follow;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.UserRepository;

// Service handling follow and unfollow business logic
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    // Follows a user and updates follower/following counts
    @Transactional
    public void followUser(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("Users cannot follow themselves");
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("Follower not found"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new ResourceNotFoundException("User to follow not found"));

        if (followRepository.existsByFollowerAndFollowing(follower, following)) {
            return; // Already following
        }

        Follow follow = Follow.builder()
                .follower(follower)
                .following(following)
                .build();
        followRepository.save(follow);

        // Update counts in User entities
        following.setFollowerCount(following.getFollowerCount() + 1);
        follower.setFollowingCount(follower.getFollowingCount() + 1);
        userRepository.save(following);
        userRepository.save(follower);
    }

    // Unfollows a user and updates follower/following counts
    @Transactional
    public void unfollowUser(Long followerId, Long followingId) {
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResourceNotFoundException("Follower not found"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new ResourceNotFoundException("User to unfollow not found"));

        Optional<Follow> followOpt = followRepository.findByFollowerAndFollowing(follower, following);
        if (followOpt.isPresent()) {
            followRepository.delete(followOpt.get());

            // Update counts in User entities (prevent negative counts)
            following.setFollowerCount(Math.max(0, following.getFollowerCount() - 1));
            follower.setFollowingCount(Math.max(0, follower.getFollowingCount() - 1));
            userRepository.save(following);
            userRepository.save(follower);
        }
    }

    // Retrieves paginated list of followers for a user
    public Page<UserFollowDto> getFollowers(Long userId, Long currentUserId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;

        return followRepository.findByFollowing(user, pageable)
                .map(follow -> {
                    User follower = follow.getFollower();
                    UserFollowDto dto = userMapper.toUserFollowDto(follower);
                    // Check if current user is following the follower
                    boolean isFollowing = currentUser != null && followRepository.existsByFollowerAndFollowing(currentUser, follower);
                    return dto.toBuilder().isFollowing(isFollowing).build();
                });
    }

    // Retrieves paginated list of users followed by a user
    public Page<UserFollowDto> getFollowing(Long userId, Long currentUserId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;

        return followRepository.findByFollower(user, pageable)
                .map(follow -> {
                    User following = follow.getFollowing();
                    UserFollowDto dto = userMapper.toUserFollowDto(following);
                    // Check if current user is following the user in the list
                    boolean isFollowing = currentUser != null && followRepository.existsByFollowerAndFollowing(currentUser, following);
                    return dto.toBuilder().isFollowing(isFollowing).build();
                });
    }
}
