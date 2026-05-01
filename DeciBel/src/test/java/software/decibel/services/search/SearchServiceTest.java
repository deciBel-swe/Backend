package software.decibel.services.search;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.search.SearchResponse;
import software.decibel.dtos.track.responses.TrackResponse;
import software.decibel.dtos.user.UserSummaryDTO;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.mappers.UserMapper;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private software.decibel.repositories.TrackRepository trackRepository;
    @Mock
    private software.decibel.repositories.PlaylistRepository playlistRepository;
    @Mock
    private software.decibel.repositories.UserRepository userRepository;

    @Mock
    private TrackMapper trackMapper;
    @Mock
    private PlaylistMapper playlistMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private SearchService searchService;

    @Test
    void search_withInvalidType_throwsException() {
        assertThrows(ResponseStatusException.class, () -> searchService.search("test", "invalid_type", 0, 10));
    }

    @Test
    void search_withTrackType_returnsTracks() {
        Track track = new Track();
        track.setId(1L);
        Page<Track> trackPage = new PageImpl<>(List.of(track), PageRequest.of(0, 10), 1);

        when(trackRepository.searchPublicTracks(eq("test"), any(), any(Pageable.class))).thenReturn(trackPage);

        // Create a mock TrackResponse to prevent NPEs when SearchService calls .id()
        TrackResponse mockTrackResponse = mock(TrackResponse.class);
        lenient().when(mockTrackResponse.id()).thenReturn(1L);

        // Mock both potential overloaded signatures of toTrackResponse
        lenient().when(trackMapper.toTrackResponse(
                any(Track.class), any(AccountTier.class), any(), any())
        ).thenReturn(mockTrackResponse);

        lenient().when(trackMapper.toTrackResponse(
                any(Track.class), anyBoolean(), anyBoolean(), any(AccountTier.class))
        ).thenReturn(mockTrackResponse);

        SearchResponse response = searchService.search("test", "track", 0, 10);

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("TRACK", response.content().get(0).type());
        verify(trackRepository, times(1)).searchPublicTracks(eq("test"), any(), any(Pageable.class));
    }

    @Test
    void search_withUserType_returnsUsers() {
        User user = new User();
        user.setId(1L);
        Page<User> userPage = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);

        when(userRepository.searchPublicUsers(eq("test"), any(), any(Pageable.class))).thenReturn(userPage);

        // Mock UserMapper
        when(userMapper.toUserSummaryDto(any(User.class))).thenReturn(
                new UserSummaryDTO(1L, "username", "Display", "url", false, 0, 0)
        );

        SearchResponse response = searchService.search("test", "user", 0, 10);

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("USER", response.content().get(0).type());
        verify(userRepository, times(1)).searchPublicUsers(eq("test"), any(), any(Pageable.class));
    }

    @Test
    void search_withAll_callsAllRepositories() {
        when(trackRepository.searchPublicTracks(anyString(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(playlistRepository.searchPublicPlaylists(anyString(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));
        when(userRepository.searchPublicUsers(anyString(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(Collections.emptyList()));

        searchService.search("test", "all", 0, 30);

        verify(trackRepository).searchPublicTracks(eq("test"), any(), any(Pageable.class));
        verify(playlistRepository).searchPublicPlaylists(eq("test"), any(), any(Pageable.class));
        verify(userRepository).searchPublicUsers(eq("test"), any(), any(Pageable.class));
    }
}
