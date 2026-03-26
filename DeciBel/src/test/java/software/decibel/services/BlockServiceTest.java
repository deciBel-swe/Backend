package software.decibel.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import software.decibel.dtos.user.BlockedUserDto;
import software.decibel.entities.Block;
import software.decibel.entities.Follow;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.UserRepository;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock
    private BlockRepository blockRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private BlockService blockService;

    private User blocker;
    private User blocked;

    @BeforeEach
    void setUp() {
        blocker = User.builder().id(1L).username("blocker").followerCount(1).followingCount(1).build();
        blocked = User.builder().id(2L).username("blocked").followerCount(1).followingCount(1).build();
    }

    @Test
    void blockUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        when(blockRepository.existsByBlockerAndBlocked(blocker, blocked)).thenReturn(false);

        // Mock follow relationship in both directions
        Follow follow1 = Follow.builder().follower(blocker).following(blocked).build();
        Follow follow2 = Follow.builder().follower(blocked).following(blocker).build();
        when(followRepository.findByFollowerAndFollowing(blocker, blocked)).thenReturn(Optional.of(follow1));
        when(followRepository.findByFollowerAndFollowing(blocked, blocker)).thenReturn(Optional.of(follow2));

        blockService.blockUser(1L, 2L);

        verify(blockRepository).save(any(Block.class));
        verify(followRepository).delete(follow1);
        verify(followRepository).delete(follow2);
        
        // Check if counts were updated (decremented because of unfollow)
        assertEquals(0, blocker.getFollowerCount());
        assertEquals(0, blocker.getFollowingCount());
        assertEquals(0, blocked.getFollowerCount());
        assertEquals(0, blocked.getFollowingCount());
        
        verify(userRepository, times(4)).save(any(User.class));
    }

    @Test
    void blockUser_selfBlock_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> blockService.blockUser(1L, 1L));
    }

    @Test
    void blockUser_userNotFound_throwsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> blockService.blockUser(1L, 2L));
    }

    @Test
    void unblockUser_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));
        Block block = Block.builder().blocker(blocker).blocked(blocked).build();
        when(blockRepository.findByBlockerAndBlocked(blocker, blocked)).thenReturn(Optional.of(block));

        blockService.unblockUser(1L, 2L);

        verify(blockRepository).delete(block);
    }

    @Test
    void getBlockedUsers_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        Block block = Block.builder().blocker(blocker).blocked(blocked).build();
        Page<Block> page = new PageImpl<>(List.of(block));
        when(blockRepository.findByBlocker(eq(blocker), any(Pageable.class))).thenReturn(page);
        
        BlockedUserDto dto = new BlockedUserDto(2L, "blocked", "Blocked", "avatar.png");
        when(userMapper.toBlockedUserDto(blocked)).thenReturn(dto);

        Page<BlockedUserDto> result = blockService.getBlockedUsers(1L, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("blocked", result.getContent().get(0).username());
    }
}
