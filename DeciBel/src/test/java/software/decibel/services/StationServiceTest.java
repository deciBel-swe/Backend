package software.decibel.services;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import software.decibel.dtos.discovery.StationPageResponse;
import software.decibel.entities.Track;
import software.decibel.exceptions.custom.NoStationResultsException;
import software.decibel.mappers.StationMapper;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.TrackTokenRepository;
import software.decibel.services.user.UserService;

class StationServiceTest {

    private final Long mockUserId = 1L;

    @Mock
    private TrackRepository trackRepository;
    @Mock
    private TrackLikeRepository trackLikeRepository;
    @Mock
    private TrackRepostRepository trackRepostRepository;
    @Mock
    private TrackTokenRepository trackTokenRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private StationMapper stationMapper;
    @Mock
    private UserService userService;
    @Mock
    private PlaylistRepository playlistRepository;

    @InjectMocks
    private StationService stationService;

    private MockedStatic<JwtService> jwtMock;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        jwtMock = mockStatic(JwtService.class);
        jwtMock.when(JwtService::getCurrentUserId).thenReturn(mockUserId);
    }

    @AfterEach
    void cleanup() {
        jwtMock.close();
    }

    //  helpers
    // you can pass an array of tracks
    private Page<Track> pageOf(Track... tracks) {
        return new PageImpl<>(List.of(tracks));
    }

    private void mockBuildDependencies(Set<Long> trackIds) {
        when(trackLikeRepository.findTrackIdsByUserId(mockUserId)).thenReturn(Set.of());
        when(trackRepostRepository.findTrackIdsByUserId(mockUserId)).thenReturn(Set.of());
        when(trackTokenRepository.findActiveTokensByTrackIds(trackIds)).thenReturn(List.of());
        when(followRepository.findFollowingIdsByFollowerId(mockUserId)).thenReturn(List.of());
    }

    // getGenreStation
    @Test
    void getGenreStation_shouldReturnPageResponse_whenTracksFound() {
        // Arrange
        Track track = new Track();
        track.setId(1L);
        Page<Track> page = pageOf(track);
        StationPageResponse mockResponse = mock(StationPageResponse.class);

        when(trackRepository.findGenreStation(eq(mockUserId), any(PageRequest.class))).thenReturn(page);
        mockBuildDependencies(Set.of(1L));
        when(stationMapper.toPageResponse(any(Page.class), any(), any(), any(), any())).thenReturn(mockResponse);

        // Act
        StationPageResponse response = stationService.getGenreStation(0, 20);

        // Assert
        assertThat(response).isEqualTo(mockResponse);
        verify(trackRepository).findGenreStation(eq(mockUserId), any(PageRequest.class));
    }

    @Test
    void getGenreStation_shouldThrowNoStationResults_whenEmpty() {
        // Arrange
        when(trackRepository.findGenreStation(eq(mockUserId), any(PageRequest.class)))
                .thenReturn(Page.empty());
        when(trackRepository.findMostPopularTracks(any(PageRequest.class)))
                .thenReturn(Page.empty());

        // Act & Assert
        assertThrows(NoStationResultsException.class, () -> stationService.getGenreStation(0, 20));
    }

    // getArtistStation
    @Test
    void getArtistStation_shouldReturnPageResponse_whenTracksFound() {
        // Arrange
        Track track = new Track();
        track.setId(10L);
        Page<Track> page = pageOf(track);
        StationPageResponse mockResponse = mock(StationPageResponse.class);

        when(trackRepository.findArtistStation(eq(mockUserId), any(PageRequest.class)))
                .thenReturn(page);
        mockBuildDependencies(Set.of(10L));
        when(stationMapper.toPageResponse(any(Page.class), any(), any(), any(), any())).thenReturn(mockResponse);

        // Act
        StationPageResponse response = stationService.getArtistStation(0, 20);

        // Assert
        assertThat(response).isEqualTo(mockResponse);
        verify(trackRepository).findArtistStation(eq(mockUserId), any(PageRequest.class));
    }

    @Test
    void getArtistStation_shouldThrowNoStationResults_whenEmpty() {
        // Arrange
        when(trackRepository.findArtistStation(eq(mockUserId), any(PageRequest.class)))
                .thenReturn(Page.empty());
        when(trackRepository.findMostPopularArtistTracks(any(PageRequest.class)))
                .thenReturn(Page.empty());

        // Act & Assert
        assertThrows(NoStationResultsException.class, () -> stationService.getArtistStation(0, 20));
    }

    // getLikesStation
    @Test
    void getLikesStation_shouldReturnPageResponse_whenTracksFound() {
        // Arrange
        Track track = new Track();
        track.setId(5L);
        Page<Track> page = pageOf(track);
        StationPageResponse mockResponse = mock(StationPageResponse.class);

        when(trackRepository.findLikesStation(eq(mockUserId), any(PageRequest.class))).thenReturn(page);
        mockBuildDependencies(Set.of(5L));
        when(stationMapper.toPageResponse(any(Page.class), any(), any(), any(), any())).thenReturn(mockResponse);

        // Act
        StationPageResponse response = stationService.getLikesStation(0, 20);

        // Assert
        assertThat(response).isEqualTo(mockResponse);
        verify(trackRepository).findLikesStation(eq(mockUserId), any(PageRequest.class));
    }

    @Test
    void getLikesStation_shouldThrowNoStationResults_whenEmpty() {
        // Arrange
        when(trackRepository.findLikesStation(eq(mockUserId), any(PageRequest.class)))
                .thenReturn(Page.empty());
        when(trackRepository.findMostLikedTracks(any(PageRequest.class)))
                .thenReturn(Page.empty());

        // Act & Assert
        assertThrows(NoStationResultsException.class, () -> stationService.getLikesStation(0, 20));
    }
}
