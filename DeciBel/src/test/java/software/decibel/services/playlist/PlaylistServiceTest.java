package software.decibel.services.playlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.PlaylistType;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.PlaylistAccessDeniedException;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.PlaylistLikeRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistRepostRepository;
import software.decibel.repositories.PlaylistTokenRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserService;
import software.decibel.utils.FileUtilityAzure;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private FileUtilityAzure fileUtilityAzure;

    @Mock
    private PlaylistTokenRepository playlistTokenRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private TrackLikeRepository trackLikeRepository;
    @Mock
    private TrackRepostRepository trackRepostRepository;
    @Mock
    private PlaylistLikeRepository playlistLikeRepository;
    @Mock
    private PlaylistRepostRepository playlistRepostRepository;

    @Mock
    private UserService userService;

    @Mock
    private TrackMapper trackMapper;

    @Mock
    private PlaylistTokenService playlistTokenService;

    @Mock
    private PlaylistMapper playlistMapper;

    @InjectMocks
    private PlaylistService playlistService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(playlistService, "playlistTokenService", playlistTokenService);
        ReflectionTestUtils.setField(playlistService, "playlistLikeRepository", playlistLikeRepository);
        ReflectionTestUtils.setField(playlistService, "playlistRepostRepository", playlistRepostRepository);
    }

    // ── createPlaylist ────────────────────────────────────────────────────────
    @Test
    void createPlaylist_whenRequestIsValid_returnsPlaylistResponse() {
        User user = user(1L);
        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setTitle("My Playlist");

        when(userService.getUserIfExistsById(anyLong())).thenReturn(user);
        when(playlistRepository.existsBySlug(anyString())).thenReturn(false);
        when(playlistMapper.toEntity(any(CreatePlaylistRequest.class), eq(user), anyString(), isNull())).thenReturn(playlist);
        when(playlistRepository.save(any(Playlist.class))).thenReturn(playlist);
        when(playlistTokenService.resolveSecretToken(any(Playlist.class))).thenReturn("mock-token");
        PlaylistResponse mockResponse = new PlaylistResponse(
                10L, "My Playlist", PlaylistType.PLAYLIST, false, false, null, false,
                null, null, 0, 0,
                new software.decibel.dtos.user.UserSummaryDTO(1L, "testuser_1", null, null, false, 0, 0),
                List.of(), null, null, "mock-token", null);
        when(playlistMapper.toResponse(eq(playlist), any(Pageable.class), anyString())).thenReturn(mockResponse);

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                "My Playlist", "desc", PlaylistType.PLAYLIST, false, null);

        PlaylistResponse response = playlistService.createPlaylist(1L, request);

        assertEquals("My Playlist", response.title());
        verify(playlistRepository).save(any(Playlist.class));
    }

    @Test
    void createPlaylist_whenUserNotFound_throwsNotFoundException() {
        when(userService.getUserIfExistsById(anyLong()))
                .thenThrow(new ResourceNotFoundException("User not found"));

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                "My Playlist", null, PlaylistType.PLAYLIST, false, null);

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.createPlaylist(99L, request));

        verify(playlistRepository, never()).save(any());
    }

    @Test
    void createPlaylist_withCoverArt_uploadsToAzure() {
        User user = user(1L);
        var mockFile = mock(org.springframework.web.multipart.MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                "My Album", "desc", PlaylistType.ALBUM, true, mockFile);

        Playlist playlist = new Playlist();
        playlist.setId(11L);
        playlist.setTitle("My Album");
        playlist.setCoverArtUrl("https://azure.com/cover.jpg");

        when(userService.getUserIfExistsById(anyLong())).thenReturn(user);
        when(playlistRepository.existsBySlug(anyString())).thenReturn(false);
        when(fileUtilityAzure.saveFile(any(), any())).thenReturn("https://azure.com/cover.jpg");
        when(playlistMapper.toEntity(any(CreatePlaylistRequest.class), eq(user), anyString(), anyString())).thenReturn(playlist);
        when(playlistRepository.save(any(Playlist.class))).thenReturn(playlist);
        when(playlistTokenService.issueNewToken(any(Playlist.class))).thenReturn("mock-token");
        when(playlistTokenService.resolveSecretToken(any(Playlist.class))).thenReturn("mock-token");
        PlaylistResponse mockResponse = new PlaylistResponse(
                11L, "My Album", PlaylistType.ALBUM, true, false, null, true,
                "https://azure.com/cover.jpg", null, 0, 0,
                new software.decibel.dtos.user.UserSummaryDTO(1L, "testuser_1", null, null, false, 0, 0),
                List.of(), null, null, "mock-token", null);
        when(playlistMapper.toResponse(eq(playlist), any(Pageable.class), anyString())).thenReturn(mockResponse);

        PlaylistResponse response = playlistService.createPlaylist(user.getId(), request);

        assertEquals("https://azure.com/cover.jpg", response.coverArtUrl());
        verify(fileUtilityAzure).saveFile(any(), any());
    }

    // ── patchPlaylist ─────────────────────────────────────────────────────────
    @Test
    void patchPlaylist_whenUserIsNotOwner_throwsForbidden() {
        User owner = user(10L);
        Playlist playlist = playlist(owner);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        PatchPlaylistRequest request = new PatchPlaylistRequest("New Title", null, null, null, null);

        assertThrows(PlaylistAccessDeniedException.class,
                () -> playlistService.patchPlaylist(99L, 10L, request));

        verify(playlistRepository, never()).save(any(Playlist.class));
    }

    @Test
    void patchPlaylist_whenPlaylistNotFound_throwsNotFoundException() {
        when(playlistRepository.findById(99L)).thenReturn(Optional.empty());

        PatchPlaylistRequest request = new PatchPlaylistRequest(null, "new desc", null, null, null);

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.patchPlaylist(1L, 99L, request));
    }

    // ── getPlaylist ───────────────────────────────────────────────────────────
    @Test
    void getPlaylist_whenExists_returnsPlaylistResponse() {
        User user = user(1L);
        user.setTier(AccountTier.FREE);
        Playlist playlist = playlist(user);
        when(userService.getUserIfExistsById(user.getId())).thenReturn(user);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackLikeRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(trackRepostRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(blockRepository.existsByBlocker_IdAndBlocked_Id(anyLong(), anyLong())).thenReturn(false);
        when(playlistTokenService.resolveSecretToken(any(Playlist.class))).thenReturn("mock-token");
        PlaylistResponse mockResponse = new PlaylistResponse(
                10L, "Old Title", PlaylistType.PLAYLIST, false, false, null, false,
                null, null, 0, 0,
                new software.decibel.dtos.user.UserSummaryDTO(1L, "testuser_1", null, null, false, 0, 0),
                List.of(), null, null, "mock-token", null);
        when(playlistMapper.toResponse(any(Playlist.class), anySet(), anySet(), anyBoolean(), anyBoolean(), any(AccountTier.class), any(Pageable.class), anyString())).thenReturn(mockResponse);

        try (MockedStatic<JwtService> mockedJwt = mockStatic(JwtService.class)) {
            mockedJwt.when(JwtService::getCurrentUserId).thenReturn(1L);

            Pageable pageable = PageRequest.of(0, 10);
            PlaylistResponse response = playlistService.getPlaylist(10L, 1L, pageable);

            assertEquals(10L, response.id());
            assertEquals(1L, response.owner().id());
        }
    }

    // ── addTrack ──────────────────────────────────────────────────────────────
    @Test
    void addTrack_whenValid_addsTrackAndUpdatesCounts() {
        User user = user(1L);
        Playlist playlist = playlist(user);
        Track track = track(100L, "Hip Hop", 180, user);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(100L)).thenReturn(Optional.of(track));
        when(playlistRepository.save(any())).thenReturn(playlist);
        when(playlistTokenService.resolveSecretToken(any(Playlist.class))).thenReturn("mock-token");
        PlaylistResponse mockResponse = new PlaylistResponse(
                10L, "Old Title", PlaylistType.PLAYLIST, false, false, null, false,
                null, null, 180, 1,
                new software.decibel.dtos.user.UserSummaryDTO(1L, "testuser_1", null, null, false, 0, 0),
                List.of("Hip Hop"), null, null, "mock-token", null);
        when(playlistMapper.toResponse(any(Playlist.class), any(Pageable.class), anyString())).thenReturn(mockResponse);

        try (MockedStatic<JwtService> mockedJwt = mockStatic(JwtService.class)) {
            mockedJwt.when(JwtService::getCurrentUserId).thenReturn(1L);

            PlaylistResponse response = playlistService.addTrack(1L, 10L, 100L);

            assertEquals(1, response.trackCount());
            assertEquals(180, response.totalDurationSeconds());
            verify(playlistRepository).save(any());
        }
    }

    @Test
    void addTrack_whenTrackNotFound_throwsNotFoundException() {
        User owner = user(10L);
        Playlist playlist = playlist(owner);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        when(trackRepository.findById(999L)).thenReturn(Optional.empty());

        try (MockedStatic<JwtService> mockedJwt = mockStatic(JwtService.class)) {
            mockedJwt.when(JwtService::getCurrentUserId).thenReturn(10L);

            assertThrows(ResourceNotFoundException.class,
                    () -> playlistService.addTrack(10L, 10L, 999L));

        }
    }

    // ── removeTrack ───────────────────────────────────────────────────────────
    @Test
    void removeTrack_whenValid_removesTrackAndUpdatesCounts() {
        User user = user(1L);
        Track track = track(100L, "Hip Hop", 180, user);
        Playlist playlist = playlist(user);
        playlist.getTracks().add(track);
        playlist.setTrackCount(1);
        playlist.setTotalDurationSeconds(180);
        playlist.setGenres(new ArrayList<>(List.of("Hip Hop")));

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(100L)).thenReturn(Optional.of(track));
        when(playlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playlistTokenService.resolveSecretToken(any(Playlist.class))).thenReturn("mock-token");

        playlistService.removeTrack(1L, 10L, 100L);

        assertEquals(0, playlist.getTrackCount());
        assertEquals(0, playlist.getTotalDurationSeconds());
        assertTrue(playlist.getGenres().isEmpty());
        verify(playlistRepository).save(any());
    }

    @Test
    void removeTrack_whenTrackNotInPlaylist_throwsNotFoundException() {
        Playlist playlist = playlist(user(1L));
        Track track = track(100L, "Pop", 200, user(1L));

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(100L)).thenReturn(Optional.of(track));

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.removeTrack(1L, 10L, 100L));
    }

    @Test
    void removeTrack_whenUserIsNotOwner_throwsForbidden() {
        User owner = user(10L); // FIX: Passed ID
        Playlist playlist = playlist(owner);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        // FIX: Changed Expected Exception to PlaylistAccessDeniedException
        assertThrows(PlaylistAccessDeniedException.class,
                () -> playlistService.removeTrack(99L, 10L, 100L));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private User user(Long id) {
        return User.builder()
                .id(id)
                .username("testuser_" + id)
                .build();
    }

    private Playlist playlist(User user) {
        return Playlist.builder()
                .id(10L)
                .title("Old Title")
                .slug("old-title")
                .type(PlaylistType.PLAYLIST)
                .isPrivate(false)
                .user(user)
                .tracks(new ArrayList<>())
                .genres(new ArrayList<>())
                .build();
    }

    private Track track(Long id, String genre, int durationSeconds, User uploader) {
        Track track = new Track();
        track.setId(id);
        track.setGenre(genre);
        track.setDurationSeconds(durationSeconds);
        track.setUploader(uploader);
        track.setVisibility(Visibility.PUBLIC);
        return track;
    }
}
