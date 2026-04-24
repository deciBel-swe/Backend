package software.decibel.services;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.decibel.dtos.user.BlockedUserDto;
import software.decibel.entities.Block;
import software.decibel.entities.Follow;
import software.decibel.entities.User;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.user.UserService;

/**
 * Service handling user blocking and unblocking business logic.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserService userService;

    /**
     * Blocks a user and removes any existing follow relationship between them.
     *
     * @param blockerId the ID of the user performing the block
     * @param blockedId the ID of the user being blocked
     */
    @Transactional
    public void blockUser(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Users cannot block themselves");
        }

        User blocker = userService.getUserIfExistsById(blockerId);
        User blocked = userService.getUserIfExistsById(blockedId);

        if (blockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            return; // Already blocked
        }

        // Create the block
        Block block = Block.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build();
        blockRepository.save(block);

        // Remove follow relationships in both directions
        removeFollowRelationship(blocker, blocked);
        removeFollowRelationship(blocked, blocker);
    }

    /**
     * Unblocks a user.
     *
     * @param blockerId the ID of the user performing the unblock
     * @param blockedId the ID of the user being unblocked
     */
    @Transactional
    public void unblockUser(Long blockerId, Long blockedId) {
        User blocker = userService.getUserIfExistsById(blockerId);
        User blocked = userService.getUserIfExistsById(blockedId);

        blockRepository.findByBlockerAndBlocked(blocker, blocked)
                .ifPresent(blockRepository::delete);
    }

    /**
     * Retrieves a paginated list of users blocked by a specific user.
     *
     * @param blockerId the ID of the user whose blocked list is being retrieved
     * @param pageable  pagination information
     * @return a page of blocked user DTOs
     */
    public Page<BlockedUserDto> getBlockedUsers(Long blockerId, Pageable pageable) {
        User blocker = userService.getUserIfExistsById(blockerId);

        return blockRepository.findByBlocker(blocker, pageable)
                .map(block -> userMapper.toBlockedUserDto(block.getBlocked()));
    }

    /**
     * Helper method to remove a follow relationship and update user counts.
     */
    private void removeFollowRelationship(User follower, User following) {
        Optional<Follow> followOpt = followRepository.findByFollowerAndFollowing(follower, following);
        if (followOpt.isPresent()) {
            followRepository.delete(followOpt.get());

            // Update counts in User entities
            following.setFollowerCount(Math.max(0, following.getFollowerCount() - 1));
            follower.setFollowingCount(Math.max(0, follower.getFollowingCount() - 1));
            userRepository.save(following);
            userRepository.save(follower);
        }
    }

  public boolean hasUserBlocked(Long blockerId, Long blockedId) {
    return blockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId);
  }

  public boolean isBlockedByUser(Long blockedId, Long blockerId) {
    return blockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId);
  }
}
