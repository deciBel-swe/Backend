package software.decibel.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.domain.*;
import software.decibel.dtos.track.*;
import software.decibel.entities.*;
import software.decibel.enums.*;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.user.UserService;
import software.decibel.utils.*;

class TrackServiceTest {

  @Mock private TrackRepository trackRepository;
  @Mock private UserService userService;
  @Mock private FileUtilityAzure fileUtilityAzure;
  @Mock private WaveFormUtility waveFormUtility;
  @Mock private AudioUtility audioUtility;
  @Mock private TrackMapper trackMapper;
  @Mock private TagService tagService;

  // only one actually there but the rest are injected inside it and are not real
  // we tell these mocks how to act using when().then()''
  @InjectMocks private TrackService trackService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
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
    when(trackRepository.findByUploaderId(eq(userId), any(Pageable.class))).thenReturn(page);

    // when mapper converts page ->  return our fake response
    when(trackMapper.toPageResponse(page)).thenReturn(response);

    when(userService.getUserIfExistsById(userId)).thenReturn(new User());

    // -------------------- Act --------------------

    TrackPageResponse result = trackService.getUserTracks(userId, 0, 10);

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
}
