package software.decibel.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.decibel.dtos.track.requests.TrackPatchRequest;
import software.decibel.dtos.track.responses.TrackPageResponse;
import software.decibel.dtos.track.responses.TrackStatusResponse;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.CommentRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.track.TrackService;
import software.decibel.services.user.UserService;
import software.decibel.utils.FileUtilityAzure;
import software.decibel.utils.TrackChecksUtil;

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
    when(trackChecksUtil.getTrackIfExistsById(1L)).thenReturn(track);

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
