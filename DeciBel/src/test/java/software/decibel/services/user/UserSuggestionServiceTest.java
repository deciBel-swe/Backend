package software.decibel.services.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import software.decibel.dtos.user.UserFollowDto;
import software.decibel.entities.User;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSuggestionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserSuggestionService userSuggestionService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder()
                .id(1L)
                .username("current")
                .favoriteGenres(List.of("Rock", "Pop"))
                .build();
    }

    @Test
    void getSuggestedUsers_withFavoriteGenres_returnsSuggestions() {
        User suggestedUser = User.builder()
                .id(2L)
                .username("suggested")
                .displayName("Suggested User")
                .avatarUrl("avatar.png")
                .build();

        when(trackRepository.findGenresOfLikedTracksByUserId(1L)).thenReturn(List.of("Jazz"));
        when(userRepository.findSuggestedUsersByGenres(eq(1L), anyList(), any(Pageable.class)))
                .thenReturn(List.of(suggestedUser));
        
        UserFollowDto dto = UserFollowDto.builder()
                .id(2L)
                .username("suggested")
                .displayName("Suggested User")
                .avatarUrl("avatar.png")
                .isFollowing(false)
                .build();
        when(userMapper.toUserFollowDto(suggestedUser)).thenReturn(dto);

        List<UserFollowDto> result = userSuggestionService.getSuggestedUsers(currentUser, 5);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("suggested", result.get(0).username());
        assertEquals("Suggested User", result.get(0).displayName());
        assertFalse(result.get(0).isFollowing());
    }

    @Test
    void getSuggestedUsers_noInterests_returnsEmptyList() {
        currentUser.setFavoriteGenres(new ArrayList<>());
        when(trackRepository.findGenresOfLikedTracksByUserId(1L)).thenReturn(new ArrayList<>());

        List<UserFollowDto> result = userSuggestionService.getSuggestedUsers(currentUser, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getSuggestedUsers_nullFavoriteGenres_handlesGracefully() {
        currentUser.setFavoriteGenres(null);
        when(trackRepository.findGenresOfLikedTracksByUserId(1L)).thenReturn(List.of("Rock"));
        when(userRepository.findSuggestedUsersByGenres(eq(1L), anyList(), any(Pageable.class)))
                .thenReturn(new ArrayList<>());

        List<UserFollowDto> result = userSuggestionService.getSuggestedUsers(currentUser, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
