package software.decibel.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import software.decibel.dtos.track.requests.TrackPatchRequest;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.track.responses.TrackPageResponse;
import software.decibel.dtos.track.responses.TrackStatusResponse;
import software.decibel.entities.ListeningHistory;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.CooldownActiveException;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.CommentRepository;
import software.decibel.repositories.ListeningHistoryRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.track.TrackService;
import software.decibel.services.user.UserService;
import software.decibel.utils.AudioUtility;
import software.decibel.utils.FileUtilityAzure;
import software.decibel.utils.TrackChecksUtil;
import software.decibel.utils.WaveFormUtility;
import tools.jackson.databind.ObjectMapper;

class TrackServiceTest {

    @Mock
    private TrackLikeRepository likeRepository;
    @Mock
    private TrackRepostRepository repostRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private TrackRepository trackRepository;

    @Mock
    private ListeningHistoryRepository listeningHistoryRepository;

    @Mock
    private UserService userService;
    @Mock
    private FileUtilityAzure fileUtilityAzure;
    @Mock
    private TrackMapper trackMapper;
    @Mock
    private TagService tagService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LikeService likeService;

    @Mock
    private RepostService repostService;

    @Mock
    private TrackChecksUtil trackChecksUtil;

    @InjectMocks
    private TrackService trackService;
    private MockedStatic<JwtService> jwtMock;
    private final Long mockUserId = 1L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jwtMock = mockStatic(JwtService.class);
        jwtMock.when(JwtService::getCurrentUserId).thenReturn(mockUserId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @AfterEach
    void tearDown() {
        jwtMock.close();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private Track createTrack(Long id) {
        User uploader = new User();
        uploader.setId(mockUserId); // Owner is 1L (matches the mocked JWT user)

        Track track = new Track();
        track.setId(id);
        track.setUploader(uploader);
        track.setVisibility(Visibility.PUBLIC);
        return track;
    }

    @Test
    void recordTrackPlay_forGuest_incrementsPlayCountWithoutSavingHistory() {
        Track track = createTrack(5L);
        track.setPlayCount(3);

        jwtMock.when(JwtService::getCurrentUserId).thenReturn(null);
        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));
        when(trackRepository.save(track)).thenReturn(track);

        MessageResponse result = trackService.recordTrackPlay(5L);

        assertEquals("Play recorded", result.message());
        assertEquals(4, track.getPlayCount());
        verify(trackRepository).save(track);
        verify(listeningHistoryRepository, never()).save(any());
    }

    @Test
    void recordTrackPlay_forAuthenticatedUser_savesHistoryAndIncrementsPlayCount() {
        Track track = createTrack(5L);
        track.setPlayCount(0);
        track.setDurationSeconds(120);
        User user = new User();
        user.setId(mockUserId);

        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));
        when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
        when(listeningHistoryRepository.findTopByUserIdAndTrackIdOrderByPlayedAtDesc(mockUserId, 5L))
                .thenReturn(Optional.empty());
        when(trackRepository.save(track)).thenReturn(track);

        MessageResponse result = trackService.recordTrackPlay(5L);

        assertEquals("Play recorded", result.message());
        assertEquals(1, track.getPlayCount());
        verify(listeningHistoryRepository).save(any(ListeningHistory.class));
        verify(trackRepository).save(track);
    }

    @Test
    void recordTrackPlay_whenCooldownIsActive_throwsException() {
        Track track = createTrack(5L);
        track.setDurationSeconds(120);
        User user = new User();
        user.setId(mockUserId);
        ListeningHistory lastPlay = ListeningHistory.builder()
                .track(track)
                .user(user)
                .playedAt(LocalDateTime.now().minusSeconds(30))
                .build();

        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));
        when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
        when(listeningHistoryRepository.findTopByUserIdAndTrackIdOrderByPlayedAtDesc(mockUserId, 5L))
                .thenReturn(Optional.of(lastPlay));

        assertThrows(CooldownActiveException.class, () -> trackService.recordTrackPlay(5L));

        verify(trackRepository, never()).save(any(Track.class));
        verify(listeningHistoryRepository, never()).save(any(ListeningHistory.class));
    }

    @Test
    void recordTrackCompletion_incrementsCompletedCountAndUpdatesPlayThroughRate() {
        Track track = createTrack(5L);
        track.setPlayCount(4);
        track.setCompletedPlayCount(1);
        User user = new User();
        user.setId(mockUserId);
        ListeningHistory history = ListeningHistory.builder()
                .user(user)
                .track(track)
                .completed(false)
                .build();

        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));
        when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
        when(listeningHistoryRepository.findTopByUserIdAndTrackIdAndCompletedFalseOrderByPlayedAtDesc(mockUserId, 5L))
                .thenReturn(Optional.of(history));
        when(trackRepository.save(track)).thenReturn(track);

        MessageResponse result = trackService.recordTrackCompletion(5L);

        assertEquals("Full listen recorded", result.message());
        assertEquals(2, track.getCompletedPlayCount());
        assertEquals(0.5, track.getPlayThroughRate());
        verify(listeningHistoryRepository).save(history);
        verify(trackRepository).save(track);
    }

    @Test
    void recordTrackCompletion_whenPlayCountIsZero_doesNotDivideByZero() {
        Track track = createTrack(5L);
        track.setPlayCount(0);
        track.setCompletedPlayCount(0);
        track.setPlayThroughRate(0.0);
        User user = new User();
        user.setId(mockUserId);

        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));
        when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
        when(listeningHistoryRepository.findTopByUserIdAndTrackIdAndCompletedFalseOrderByPlayedAtDesc(mockUserId, 5L))
                .thenReturn(Optional.empty());
        when(trackRepository.save(track)).thenReturn(track);

        MessageResponse result = trackService.recordTrackCompletion(5L);

        assertEquals("Full listen recorded", result.message());
        assertEquals(0, track.getCompletedPlayCount());
        assertEquals(0.0, track.getPlayThroughRate());
        verify(trackRepository).save(track);
    }

    @Test
    void recordTrackCompletion_whenNoUncompletedPlayExists_doesNotIncrementCompletedCount() {
        Track track = createTrack(5L);
        track.setPlayCount(3);
        track.setCompletedPlayCount(3);
        track.setPlayThroughRate(1.0);
        User user = new User();
        user.setId(mockUserId);

        when(trackRepository.findById(5L)).thenReturn(Optional.of(track));
        when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
        when(listeningHistoryRepository.findTopByUserIdAndTrackIdAndCompletedFalseOrderByPlayedAtDesc(mockUserId, 5L))
                .thenReturn(Optional.empty());
        when(trackRepository.save(track)).thenReturn(track);

        MessageResponse result = trackService.recordTrackCompletion(5L);

        assertEquals("Full listen recorded", result.message());
        assertEquals(3, track.getCompletedPlayCount());
        assertEquals(1.0, track.getPlayThroughRate());
        verify(listeningHistoryRepository, never()).save(any(ListeningHistory.class));
        verify(trackRepository).save(track);
    }

    // getTrackIfExistsById
    // -------------------------------
    @Test
    void shouldReturnTrack_whenTrackExists() {
        // Arrange
        Track track = createTrack(1L);
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenReturn(track);

        // Act
        Track result = trackChecksUtil.getTrackIfExistsById(1L);

        // Assert
        assertEquals(1L, result.getId());
    }

    @Test
    void shouldThrowException_whenTrackNotFound() {
        // Arrange
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenThrow(new ResourceNotFoundException("not found"));

        // Act & Assert
        ResourceNotFoundException ex1 = assertThrows(ResourceNotFoundException.class, () -> trackChecksUtil.getTrackIfExistsById(1L));
    }

    @Test
    void shouldThrow_whenTrackNotFound_updateTrack() {
        // Arrange
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenThrow(new ResourceNotFoundException("not found"));
        TrackPatchRequest request = mock(TrackPatchRequest.class);

        // Act Assert
        ResourceNotFoundException ex2 = assertThrows(ResourceNotFoundException.class, () -> trackService.updateTrack(1L, request));
    }

    // createUploadingTrack
    // -------------------------------
    @Test
    void shouldSetStateUploading_whenCreatingTrack() {
        // Arrange
        Track track = createTrack(1L);
        String uploadId = "test-uuid-1234"; // Added client upload ID
        when(trackRepository.save(track)).thenReturn(track);

        // Act
        Track result = trackService.createUploadingTrack(track, uploadId);

        // Assert
        assertEquals(TrackState.UPLOADING, result.getState());
        verify(trackRepository).save(track);
        verify(messagingTemplate)
                .convertAndSend(
                        eq("/topic/track-status/" + uploadId), // Now uses uploadId
                        argThat(
                                (TrackStatusResponse response)
                                -> response.trackState() == TrackState.UPLOADING
                                && response.trackId().equals(1L)
                                && response.progressPercentage() != null
                                && response.progressPercentage() == 0
                                && "Initializing".equals(response.stepName())
                        )
                );
    }

    // deleteTrackCover
    // -------------------------------
    @Test
    void shouldDeleteCover_whenCoverExists() {
        // Arrange
        Track track = createTrack(1L);
        track.setCoverUrl("cover-url");
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenReturn(track);

        // Act
        trackService.deleteTrackCover(1L);

        // Assert
        verify(fileUtilityAzure).deleteFileByUrl("cover-url");
        assertNull(track.getCoverUrl());
    }

    @Test
    void shouldNotDeleteCover_whenNoCover() {
        // Arrange
        Track track = createTrack(1L);
        track.setCoverUrl(null);
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenReturn(track);

        // Act
        trackService.deleteTrackCover(1L);

        // Assert
        verify(fileUtilityAzure, never()).deleteFileByUrl(any());
    }

    @Test
    void shouldThrow_whenTrackNotFound_deleteCover() {
        // Arrange
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenThrow(new ResourceNotFoundException("not found"));

        // Act & Assert
        ResourceNotFoundException ex3 = assertThrows(ResourceNotFoundException.class, () -> trackService.deleteTrackCover(1L));
    }

    // deleteTrackAudio
    // -------------------------------
    @Test
    void shouldDeleteAudio_whenExists() {
        // Arrange
        Track track = createTrack(1L);
        track.setTrackUrl("audio-url");
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenReturn(track);

        // Act
        trackService.deleteTrackAudio(1L);

        // Assert
        verify(fileUtilityAzure).deleteFileByUrl("audio-url");
        assertNull(track.getTrackUrl());
    }

    @Test
    void shouldNotDeleteAudio_whenNull() {
        // Arrange
        Track track = createTrack(1L);
        track.setTrackUrl(null);
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenReturn(track);

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
        Track track = createTrack(1L);
        track.setWaveformUrl("wave-url");
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenReturn(track);

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
        Track track = createTrack(1L);

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
        Track track = createTrack(1L);
        track.setGenre("Rock");

        TrackPatchRequest request = mock(TrackPatchRequest.class);
        when(request.title()).thenReturn("New Title");
        when(request.genre()).thenReturn("Rock");
        when(request.description()).thenReturn("Description");
        when(request.releaseDate()).thenReturn(LocalDate.now());
        when(request.isPrivate()).thenReturn(true);
        when(request.coverImage()).thenReturn(null);
        when(request.tags()).thenReturn(null);

        when(trackChecksUtil.getTrackIfExistsById(1L)).thenReturn(track);
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
        Track track = createTrack(1L);
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

    @Test
    void shouldThrow_whenTrackNotFound_deleteAudio() {
        // Arrange
        when(trackChecksUtil.getTrackIfExistsById(1L)).thenThrow(new ResourceNotFoundException("not found"));

        // Act & Assert
        ResourceNotFoundException ex4 = assertThrows(ResourceNotFoundException.class, () -> trackService.deleteTrackAudio(1L));
    }

    @Test
    void shouldReturnTrendingTracks() {
        Track track = createTrack(1L);
        Page<Track> page = new PageImpl<>(List.of(track));
        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setTier(AccountTier.FREE);

        when(userService.getUserIfExistsById(any())).thenReturn(mockUser);

        when(trackRepository.findAllTrending(any())).thenReturn(page);
        when(likeService.getLikedTrackIds(any())).thenReturn(Set.of());
        when(repostService.getRepostedTrackIds(any())).thenReturn(Set.of());

        when(trackMapper.toPageResponse(any(), any(), any(), any()))
                .thenReturn(new TrackPageResponse(List.of(), 0, 10, 1, 1, true));

        TrackPageResponse result = trackService.getTrendingTracks(0, 10);

        assertNotNull(result);
        verify(trackRepository).findAllTrending(any());
    }

    @Test
    void shouldDeleteTrackCompletely() {
        // Arrange
        Track track = createTrack(1L);

        User user = new User();
        user.setTrackCount(0);

        when(trackChecksUtil.getTrackIfExistsById(1L)).thenReturn(track);
        when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);

        // Act
        trackService.deleteTrack(1L);

        // Assert
        verify(trackRepository).delete(track);
    }
}
