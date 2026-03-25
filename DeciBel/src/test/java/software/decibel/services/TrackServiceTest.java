package software.decibel.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import software.decibel.dtos.track.*;
import software.decibel.entities.*;
import software.decibel.enums.*;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.LikeMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.LikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.JwtService;
import software.decibel.services.track.TrackService;
import software.decibel.services.user.UserService;
import software.decibel.utils.*;
import tools.jackson.databind.ObjectMapper;

class TrackServiceTest {

  @Mock private TrackRepository trackRepository;
  @Mock private LikeRepository likeRepository;
  @Mock private UserService userService;
  @Mock private FileUtilityAzure fileUtilityAzure;
  @Mock private WaveFormUtility waveFormUtility;
  @Mock private AudioUtility audioUtility;
  @Mock private TrackMapper trackMapper;
  @Mock private LikeMapper likeMapper;
  @Mock private TagService tagService;
  @Mock private ObjectMapper objectMapper;

  // only one actually there but the rest are injected inside it and are not real
  // we tell these mocks how to act using when().then()''
  @InjectMocks private TrackService trackService;
  private MockedStatic<JwtService> jwtMock;
  private final Long mockUserId = 1L;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    jwtMock = mockStatic(JwtService.class);
    jwtMock.when(JwtService::getCurrentUserId).thenReturn(mockUserId);
  }

  @AfterEach
  void tearDown() {
    jwtMock.close();
  }

  // getTrackIfExistsById
  // -------------------------------

  @Test
  void shouldReturnTrack_whenTrackExists() {
    // Arrange
    Track track = new Track();
    track.setId(1L);
    when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

    // Act
    Track result = trackService.getTrackIfExistsById(1L);

    // Assert
    assertEquals(1L, result.getId());
  }

  @Test
  void shouldThrowException_whenTrackNotFound() {
    // Arrange
    when(trackRepository.findById(1L)).thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(ResourceNotFoundException.class, () -> trackService.getTrackIfExistsById(1L));
  }

  // updateTrackState
  // -------------------------------

  @Test
  void shouldUpdateTrackState() {
    // Arrange
    Track track = new Track();

    // Act
    trackService.updateTrackState(track, TrackState.PROCESSING);

    // Assert
    assertEquals(TrackState.PROCESSING, track.getState());
    // make sure it saved to repo
    verify(trackRepository).save(track);
  }

  @Test
  void shouldThrow_whenTrackNotFound_updateTrack() { // new
    // Arrange
    when(trackRepository.findById(1L)).thenReturn(Optional.empty());
    TrackPatchRequest request = mock(TrackPatchRequest.class);

    // Act Assert
    assertThrows(ResourceNotFoundException.class, () -> trackService.updateTrack(1L, request));
  }

  // createUploadingTrack
  // -------------------------------

  @Test
  void shouldSetStateUploading_whenCreatingTrack() {
    // Arrange
    Track track = new Track();
    when(trackRepository.save(track)).thenReturn(track);

    // Act
    Track result = trackService.createUploadingTrack(track);

    // Assert
    assertEquals(TrackState.UPLOADING, result.getState());
  }

  // deleteTrackCover
  // -------------------------------

  @Test
  void shouldDeleteCover_whenCoverExists() {
    // Arrange
    Track track = new Track();
    track.setCoverUrl("cover-url");
    when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

    // Act
    trackService.deleteTrackCover(1L);

    // Assert
    // make sure it was called
    verify(fileUtilityAzure).deleteFileByUrl("cover-url");
    assertNull(track.getCoverUrl());
  }

  @Test
  void shouldNotDeleteCover_whenNoCover() {
    // Arrange
    Track track = new Track();
    track.setCoverUrl(null);
    when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

    // Act
    trackService.deleteTrackCover(1L);

    // Assert
    verify(fileUtilityAzure, never()).deleteFileByUrl(any());
  }

  @Test
  void shouldThrow_whenTrackNotFound_deleteCover() {
    // Arrange
    when(trackRepository.findById(1L)).thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(ResourceNotFoundException.class, () -> trackService.deleteTrackCover(1L));
  }

  // deleteTrackAudio
  // -------------------------------

  @Test
  void shouldDeleteAudio_whenExists() {
    // Arrange
    Track track = new Track();
    track.setTrackUrl("audio-url");
    when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

    // Act
    trackService.deleteTrackAudio(1L);

    // Assert
    verify(fileUtilityAzure).deleteFileByUrl("audio-url");
    assertNull(track.getTrackUrl());
  }

  @Test
  void shouldNotDeleteAudio_whenNull() {
    // Arrange
    Track track = new Track();
    track.setTrackUrl(null);
    when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

    // Act
    trackService.deleteTrackAudio(1L);

    // Assert
    verify(fileUtilityAzure, never()).deleteFileByUrl(any());
  }

  // deleteTrackWaveformData
  // -------------------------------

  @Test
  void shouldDeleteWaveform_whenExists() {
    // Arrange
    Track track = new Track();
    track.setWaveformUrl("wave-url");
    when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

    // Act
    trackService.deleteTrackWaveformData(1L);

    // Assert
    verify(fileUtilityAzure).deleteFileByUrl("wave-url");
    assertNull(track.getWaveformUrl());
  }

  // addTrackTags
  // -------------------------------

  @Test
  void shouldAddTagsToTrack() {
    // Arrange
    Track track = new Track();

    Tag tag = new Tag();
    tag.setTitle("Rock");

    when(tagService.getOrCreateTag("rock")).thenReturn(tag);

    // Act
    trackService.addTrackTags(track, List.of("rock"));

    // Assert
    assertEquals(1, track.getTags().size());
    verify(trackRepository).save(track);
  }

  // updateTrack
  // -------------------------------

  @Test
  void shouldUpdateBasicFields() {
    // Arrange
    Track track = new Track();

    // create a mock object
    TrackPatchRequest request = mock(TrackPatchRequest.class);
    when(request.title()).thenReturn("New Title");
    when(request.genre()).thenReturn("Rock");
    when(request.description()).thenReturn("Description");
    when(request.releaseDate()).thenReturn(LocalDate.now());
    when(request.isPrivate()).thenReturn(true);
    when(request.coverImage()).thenReturn(null);
    when(request.tags()).thenReturn(null);

    when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
    when(trackRepository.save(track)).thenReturn(track);

    // Act
    trackService.updateTrack(1L, request);

    // Assert
    assertEquals("New Title", track.getTitle());
    assertEquals("Rock", track.getGenre());
    assertEquals("Description", track.getDescription());
    assertEquals(Visibility.PRIVATE, track.getVisibility());
  }

  @Test
  void shouldNotUpdate_whenFieldsAreNull() {
    // Arrange
    Track track = new Track();
    track.setTitle("Old");

    TrackPatchRequest request = mock(TrackPatchRequest.class);
    when(request.title()).thenReturn(null);
    when(request.genre()).thenReturn(null);
    when(request.description()).thenReturn(null);
    when(request.releaseDate()).thenReturn(null);
    when(request.isPrivate()).thenReturn(null);
    when(request.coverImage()).thenReturn(null);
    when(request.tags()).thenReturn(null);

    when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

    // Act
    trackService.updateTrack(1L, request);

    // Assert
    assertEquals("Old", track.getTitle());
  }

  // pagination
  // ----------------------------
  @Test
  void shouldReturnTracksByUser() {

    // -------------------- Arrange --------------------

    Long userId = 1L;

    // create a fake Page of tracks
    Page<Track> page = new PageImpl<>(List.of(new Track()));

    // create a fake response dto
    TrackPageResponse response = mock(TrackPageResponse.class);

    // when repository is called with this userId and any pagination,
    // return the fake page
    when(trackRepository.findByUploaderIdAndVisibility(eq(userId), eq(Visibility.PUBLIC), any(Pageable.class)))
        .thenReturn(page);

    // when mapper converts page ->  return our fake response
    when(trackMapper.toPageResponse(page)).thenReturn(response);

    when(userService.getUserIfExistsById(userId)).thenReturn(new User());

    // -------------------- Act --------------------

    TrackPageResponse result = trackService.getPublicTracksByUserId(userId, 0, 10);

    // -------------------- Assert --------------------

    // check that the returned result is exactly what the mapper returned
    assertEquals(response, result);
  }

  @Test
  void shouldThrow_whenTrackNotFound_deleteAudio() { // new
    // Arrange
    when(trackRepository.findById(1L)).thenReturn(Optional.empty());

    // Act & Assert
    assertThrows(ResourceNotFoundException.class, () -> trackService.deleteTrackAudio(1L));
  }

  @Test
  void shouldDeleteTrackCompletely() { // new
    // Arrange
    Track track = new Track();
    track.setId(1L);
    when(trackRepository.findById(1L)).thenReturn(Optional.of(track));

    // Act
    trackService.deleteTrack(1L);

    // Assert
    verify(trackRepository).delete(track);
  }

  @Test
  void likeTrack_shouldSaveLikeAndIncrementCount() {
    User user = new User();
    user.setId(mockUserId);
    Track track = new Track();
    track.setId(2L);
    track.setLikeCount(0);
    LikeResponse response = new LikeResponse("Track liked", true);

    when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
    when(trackRepository.findById(2L)).thenReturn(Optional.of(track));
    when(likeRepository.existsByUserAndTrack(user, track)).thenReturn(false);
    when(likeMapper.toLikeResponse(true)).thenReturn(response);

    LikeResponse result = trackService.likeTrack(2L);

    assertEquals(response, result);
    assertEquals(1, track.getLikeCount());
    verify(likeRepository).save(any(Like.class));
    verify(trackRepository).save(track);
  }

  @Test
  void likeTrack_shouldThrowConflict_whenAlreadyLiked() {
    User user = new User();
    Track track = new Track();
    track.setId(2L);

    when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
    when(trackRepository.findById(2L)).thenReturn(Optional.of(track));
    when(likeRepository.existsByUserAndTrack(user, track)).thenReturn(true);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> trackService.likeTrack(2L));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    verify(likeRepository, never()).save(any(Like.class));
  }

  @Test
  void likeTrack_shouldThrowNotFound_whenTrackDoesNotExist() {
    User user = new User();

    when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
    when(trackRepository.findById(2L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> trackService.likeTrack(2L));

    verify(likeRepository, never()).existsByUserAndTrack(any(), any());
    verify(likeRepository, never()).save(any(Like.class));
  }

  @Test
  void unlikeTrack_shouldDeleteLikeAndDecrementCount() {
    User user = new User();
    user.setId(mockUserId);
    Track track = new Track();
    track.setId(2L);
    track.setLikeCount(2);
    Like like = Like.builder().user(user).track(track).build();
    LikeResponse response = new LikeResponse("Like removed", false);

    when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
    when(trackRepository.findById(2L)).thenReturn(Optional.of(track));
    when(likeRepository.findByUserAndTrack(user, track)).thenReturn(Optional.of(like));
    when(likeMapper.toLikeResponse(false)).thenReturn(response);

    LikeResponse result = trackService.unlikeTrack(2L);

    assertEquals(response, result);
    assertEquals(1, track.getLikeCount());
    verify(likeRepository).delete(like);
    verify(trackRepository).save(track);
  }

  @Test
  void unlikeTrack_shouldThrowNotFound_whenLikeMissing() {
    User user = new User();
    Track track = new Track();
    track.setId(2L);

    when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
    when(trackRepository.findById(2L)).thenReturn(Optional.of(track));
    when(likeRepository.findByUserAndTrack(user, track)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> trackService.unlikeTrack(2L));

    verify(likeRepository, never()).delete(any(Like.class));
  }

  @Test
  void unlikeTrack_shouldThrowNotFound_whenTrackDoesNotExist() {
    User user = new User();

    when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
    when(trackRepository.findById(2L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> trackService.unlikeTrack(2L));

    verify(likeRepository, never()).findByUserAndTrack(any(), any());
  }

  @Test
  void unlikeTrack_shouldDeleteLikeWithoutSavingTrack_whenCountAlreadyZero() {
    User user = new User();
    user.setId(mockUserId);
    Track track = new Track();
    track.setId(2L);
    track.setLikeCount(0);
    Like like = Like.builder().user(user).track(track).build();
    LikeResponse response = new LikeResponse("Like removed", false);

    when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
    when(trackRepository.findById(2L)).thenReturn(Optional.of(track));
    when(likeRepository.findByUserAndTrack(user, track)).thenReturn(Optional.of(like));
    when(likeMapper.toLikeResponse(false)).thenReturn(response);

    LikeResponse result = trackService.unlikeTrack(2L);

    assertEquals(response, result);
    assertEquals(0, track.getLikeCount());
    verify(likeRepository).delete(like);
    verify(trackRepository, never()).save(track);
  }
}
