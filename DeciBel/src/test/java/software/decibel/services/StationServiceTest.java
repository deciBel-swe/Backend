package software.decibel.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.data.domain.*;
import software.decibel.dtos.discovery.StationPageResponse;
import software.decibel.entities.Track;
import software.decibel.exceptions.custom.NoStationResultsException;
import software.decibel.mappers.StationMapper;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.TrackTokenRepository;
import software.decibel.services.user.UserService;

class StationServiceTest {

  private final Long mockUserId = 1L;

  @Mock private TrackRepository trackRepository;
  @Mock private TrackLikeRepository trackLikeRepository;
  @Mock private TrackRepostRepository trackRepostRepository;
  @Mock private TrackTokenRepository trackTokenRepository;
  @Mock private FollowRepository followRepository;
  @Mock private StationMapper stationMapper;
  @Mock private UserService userService;

  @InjectMocks private StationService stationService;

  private MockedStatic<JwtService> jwtMock;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    // we will mock jwt once here so that anytime a test or service needs to use it
    // to get user id
    // we automatically get user id 1
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
    when(trackTokenRepository.findActiveTokensByTrackIds(trackIds)).thenReturn(Map.of());
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
    when(stationMapper.toPageResponse(eq(page), any(), any(), any(), any())).thenReturn(mockResponse);

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

    // Act & Assert
    assertThrows(NoStationResultsException.class, () -> stationService.getGenreStation(0, 20));
  }

  // getArtistStation

  @Test
  void getArtistStation_shouldReturnPageResponse_whenTracksFound() {
    // Arrange
    Long artistId = 2L;
    Track track = new Track();
    track.setId(10L);
    Page<Track> page = pageOf(track);
    StationPageResponse mockResponse = mock(StationPageResponse.class);

    when(trackRepository.findArtistStation(eq(mockUserId), any(PageRequest.class)))
        .thenReturn(page);
    mockBuildDependencies(Set.of(10L));
    when(stationMapper.toPageResponse(eq(page), any(), any(), any(), any())).thenReturn(mockResponse);

    // Act
    StationPageResponse response = stationService.getArtistStation(0, 20);

    // Assert
    assertThat(response).isEqualTo(mockResponse);
    verify(trackRepository).findArtistStation(eq(mockUserId), any(PageRequest.class));
  }

  @Test
  void getArtistStation_shouldThrowNoStationResults_whenEmpty() {
    // Arrange
    Long artistId = 2L;
    when(trackRepository.findArtistStation(eq(mockUserId), any(PageRequest.class)))
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
    when(stationMapper.toPageResponse(eq(page), any(), any(), any(), any())).thenReturn(mockResponse);

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

    // Act & Assert
    assertThrows(NoStationResultsException.class, () -> stationService.getLikesStation(0, 20));
  }
}
