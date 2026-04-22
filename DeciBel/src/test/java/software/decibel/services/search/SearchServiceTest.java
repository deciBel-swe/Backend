package software.decibel.services.search;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.search.SearchResponse;
import software.decibel.dtos.track.responses.TrackResponse;
import software.decibel.dtos.user.UserSummary;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private TrackRepository trackRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TrackMapper trackMapper;
    @Mock
    private PlaylistMapper playlistMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private SearchService searchService;

    @Test
    void search_withShortQuery_throwsException() {
        assertThrows(ResponseStatusException.class, () -> searchService.search("a", "all", 0, 10));
    }

    @Test
    void search_withTracks_callsTrackRepository() {
        Track track = new Track();
        track.setTitle("Test Track");
        Page<Track> trackPage = new PageImpl<>(List.of(track));

        when(trackRepository.searchPublicTracks(anyString(), any(Pageable.class))).thenReturn(trackPage);
        when(trackMapper.toTrackResponse(any(), any(Boolean.class), any(Boolean.class)))
                .thenReturn(new TrackResponse(1L, "Test Track", null, null, null, null, null, null, false, false, null, null, 0, 0, 0, 0, false, 0, null, null, null, null, "test-track"));

        SearchResponse response = searchService.search("test", "track", 0, 10);

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("TRACK", response.content().get(0).type());
        verify(trackRepository, times(1)).searchPublicTracks(eq("test"), any(Pageable.class));
    }

    @Test
    void search_withUsers_callsUserRepository() {
        User user = new User();
        user.setUsername("testuser");
        Page<User> userPage = new PageImpl<>(List.of(user));

        when(userRepository.searchPublicUsers(anyString(), any(Pageable.class))).thenReturn(userPage);
        when(userMapper.toUserSummary(any())).thenReturn(new UserSummary(1L, "testuser", "Test User", "avatar.png"));

        SearchResponse response = searchService.search("test", "user", 0, 10);

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("USER", response.content().get(0).type());
        verify(userRepository, times(1)).searchPublicUsers(eq("test"), any(Pageable.class));
    }

    @Test
    void search_withAll_callsAllRepositories() {
        when(trackRepository.searchPublicTracks(anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(playlistRepository.searchPublicPlaylists(anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(userRepository.searchPublicUsers(anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));

        searchService.search("test", "all", 0, 30);

        verify(trackRepository).searchPublicTracks(eq("test"), any(Pageable.class));
        verify(playlistRepository).searchPublicPlaylists(eq("test"), any(Pageable.class));
        verify(userRepository).searchPublicUsers(eq("test"), any(Pageable.class));
    }

    private String eq(String test) {
        return org.mockito.ArgumentMatchers.eq(test);
    }
}
