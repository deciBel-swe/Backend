package software.decibel.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.entities.Follow;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FollowService followService;

    @Test
    void followUser_whenValid_createsFollowAndUpdateCounts() {
        User follower = User.builder().id(1L).followerCount(0).followingCount(0).build();
        User following = User.builder().id(2L).followerCount(0).followingCount(0).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(follower));
        when(userRepository.findById(2L)).thenReturn(Optional.of(following));
        when(followRepository.existsByFollowerAndFollowing(follower, following)).thenReturn(false);

        followService.followUser(1L, 2L);

        verify(followRepository).save(any(Follow.class));
        assertEquals(1, following.getFollowerCount());
        assertEquals(1, follower.getFollowingCount());
        verify(userRepository).save(follower);
        verify(userRepository).save(following);
    }

    @Test
    void followUser_whenSelfFollow_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> followService.followUser(1L, 1L));
    }

    @Test
    void followUser_whenAlreadyFollowing_doesNothing() {
        User follower = User.builder().id(1L).build();
        User following = User.builder().id(2L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(follower));
        when(userRepository.findById(2L)).thenReturn(Optional.of(following));
        when(followRepository.existsByFollowerAndFollowing(follower, following)).thenReturn(true);

        followService.followUser(1L, 2L);

        verify(followRepository, never()).save(any(Follow.class));
    }

    @Test
    void unfollowUser_whenFollowing_deletesFollowAndUpdateCounts() {
        User follower = User.builder().id(1L).followerCount(0).followingCount(1).build();
        User following = User.builder().id(2L).followerCount(1).followingCount(0).build();
        Follow follow = Follow.builder().follower(follower).following(following).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(follower));
        when(userRepository.findById(2L)).thenReturn(Optional.of(following));
        when(followRepository.findByFollowerAndFollowing(follower, following)).thenReturn(Optional.of(follow));

        followService.unfollowUser(1L, 2L);

        verify(followRepository).delete(follow);
        assertEquals(0, following.getFollowerCount());
        assertEquals(0, follower.getFollowingCount());
        verify(userRepository).save(follower);
        verify(userRepository).save(following);
    }

    @Test
    void getFollowers_returnsMappedDtos() {
        User user = User.builder().id(1L).build();
        User follower = User.builder().id(2L).username("follower").build();
        Follow follow = Follow.builder().follower(follower).following(user).build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Follow> followPage = new PageImpl<>(List.of(follow));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(followRepository.findByFollowing(user, pageable)).thenReturn(followPage);
        when(userMapper.toUserFollowDto(follower)).thenReturn(UserFollowDto.builder()
                .id(follower.getId())
                .username(follower.getUsername())
                .build());

        Page<UserFollowDto> result = followService.getFollowers(1L, null, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals("follower", result.getContent().get(0).getUsername());
        assertFalse(result.getContent().get(0).isFollowing());
    }
}
