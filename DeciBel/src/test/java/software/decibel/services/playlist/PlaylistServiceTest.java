package software.decibel.services.playlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistSummaryResponse;
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
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.user.UserService;
import software.decibel.utils.FileUtilityAzure;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private TrackRepository trackRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private PlaylistLikeRepository playlistLikeRepository;
    @Mock
    private PlaylistRepostRepository playlistRepostRepository;
    @Mock
    private TrackLikeRepository trackLikeRepository;
    @Mock
    private TrackRepostRepository trackRepostRepository;
    @Mock
    private FileUtilityAzure fileUtilityAzure;
    @Mock
    private UserService userService;
    @Mock
    private TrackMapper trackMapper;
    @Mock
    private PlaylistTokenService playlistTokenService;

    private PlaylistMapper playlistMapper;

    @InjectMocks
    private PlaylistService playlistService;

    @BeforeEach
    void setUp() {
        // PlaylistMapper is a MapStruct abstract class — cannot be instantiated directly.
        // Mappers.getMapper resolves the generated implementation at runtime.
        playlistMapper = Mappers.getMapper(PlaylistMapper.class);
        // Inject all @Autowired dependencies that the generated PlaylistMapperImpl needs.
        // UserMapper is declared in @Mapper(uses = {UserMapper.class}) so MapStruct
        // generates a field for it — it must be injected or toUserSummaryDto() NPEs.
        UserMapper userMapper = Mappers.getMapper(UserMapper.class);
        ReflectionTestUtils.setField(playlistMapper, "userMapper", userMapper);
        ReflectionTestUtils.setField(playlistMapper, "trackMapper", trackMapper);
        ReflectionTestUtils.setField(playlistMapper, "playlistTokenService", playlistTokenService);
        ReflectionTestUtils.setField(playlistService, "playlistMapper", playlistMapper);
    }

    // ── createPlaylist ────────────────────────────────────────────────────────
    @Test
    void createPlaylist_whenRequestIsValid_returnsPlaylistSummaryResponse() {
        User user = user(1L);
        when(userService.getUserIfExistsById(anyLong())).thenReturn(user);
        when(playlistRepository.existsBySlug(anyString())).thenReturn(false);
        when(playlistRepository.saveAndFlush(any(Playlist.class))).thenAnswer(inv -> {
            Playlist p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(playlistTokenService.issueNewToken(any(Playlist.class))).thenReturn("mock-token");
        when(playlistTokenService.resolveToken(anyLong())).thenReturn("mock-token");

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                "My Playlist", "desc", PlaylistType.PLAYLIST, false, null);

        PlaylistSummaryResponse response = playlistService.createPlaylist(1L, request);

        assertEquals("My Playlist", response.title());
        // Token should always be issued on create regardless of visibility
        verify(playlistTokenService, times(1)).issueNewToken(any(Playlist.class));
        verify(playlistRepository).saveAndFlush(any(Playlist.class));
    }

    @Test
    void createPlaylist_alwaysIssuesToken_regardlessOfVisibility() {
        // Regression test: previously only private playlists got a token on create.
        // Now a token is always issued so resolveSecretTokenForUser never returns
        // null for a freshly created playlist, regardless of public/private state.
        User user = user(1L);
        when(userService.getUserIfExistsById(anyLong())).thenReturn(user);
        when(playlistRepository.existsBySlug(anyString())).thenReturn(false);
        when(playlistRepository.saveAndFlush(any(Playlist.class))).thenAnswer(inv -> {
            Playlist p = inv.getArgument(0);
            p.setId(11L);
            return p;
        });
        when(playlistTokenService.issueNewToken(any())).thenReturn("token-for-public");
        when(playlistTokenService.resolveToken(anyLong())).thenReturn("token-for-public");

        CreatePlaylistRequest publicRequest = new CreatePlaylistRequest(
                "Public Playlist", null, PlaylistType.PLAYLIST, false, null);

        PlaylistSummaryResponse response = playlistService.createPlaylist(1L, publicRequest);

        assertNotNull(response.secretToken());
        verify(playlistTokenService, times(1)).issueNewToken(any());
    }

    @Test
    void createPlaylist_tokenIssuedInSameTransaction_notVisibleToSeparateTx() {
        // Verifies that resolveToken is called AFTER issueNewToken within the
        // same transaction — if they ran in separate transactions the token
        // would not be visible and resolveToken would return null.
        User user = user(1L);
        when(userService.getUserIfExistsById(anyLong())).thenReturn(user);
        when(playlistRepository.existsBySlug(anyString())).thenReturn(false);
        when(playlistRepository.saveAndFlush(any(Playlist.class))).thenAnswer(inv -> {
            Playlist p = inv.getArgument(0);
            p.setId(12L);
            return p;
        });
        when(playlistTokenService.issueNewToken(any())).thenReturn("same-tx-token");
        // resolveToken CAN see the token because it runs in the same transaction
        when(playlistTokenService.resolveToken(12L)).thenReturn("same-tx-token");

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                "Tx Test Playlist", null, PlaylistType.PLAYLIST, true, null);

        PlaylistSummaryResponse response = playlistService.createPlaylist(1L, request);

        assertEquals("same-tx-token", response.secretToken());
    }

    @Test
    void createPlaylist_whenUserNotFound_throwsNotFoundException() {
        when(userService.getUserIfExistsById(anyLong()))
                .thenThrow(new ResourceNotFoundException("User not found"));

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                "My Playlist", null, PlaylistType.PLAYLIST, false, null);

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.createPlaylist(99L, request));

        verify(playlistRepository, never()).saveAndFlush(any());
        verify(playlistTokenService, never()).issueNewToken(any());
    }

    @Test
    void createPlaylist_withCoverArt_uploadsToAzure() {
        User user = user(1L);
        var mockFile = mock(org.springframework.web.multipart.MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                "My Album", "desc", PlaylistType.ALBUM, true, mockFile);

        when(playlistRepository.existsBySlug(anyString())).thenReturn(false);
        when(fileUtilityAzure.saveFile(any(), any())).thenReturn("https://azure.com/cover.jpg");
        when(playlistRepository.saveAndFlush(any(Playlist.class))).thenAnswer(inv -> {
            Playlist p = inv.getArgument(0);
            p.setId(11L);
            return p;
        });
        when(playlistTokenService.issueNewToken(any(Playlist.class))).thenReturn("mock-token");
        when(playlistTokenService.resolveToken(anyLong())).thenReturn("mock-token");

        PlaylistSummaryResponse response = playlistService.createPlaylist(1L, request);

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

        verify(playlistRepository, never()).saveAndFlush(any(Playlist.class));
    }

    @Test
    void patchPlaylist_whenPlaylistNotFound_throwsNotFoundException() {
        when(playlistRepository.findById(99L)).thenReturn(Optional.empty());

        PatchPlaylistRequest request = new PatchPlaylistRequest(null, "new desc", null, null, null);

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.patchPlaylist(1L, 99L, request));
    }

    @Test
    void patchPlaylist_transitioningToPrivate_issuesNewToken() {
        // When a playlist goes public → private a new secret token must be
        // issued within the same transaction so it is atomically visible.
        User owner = user(1L);
        Playlist playlist = playlist(owner); // starts as isPrivate=false
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(playlistRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playlistTokenService.issueNewToken(any())).thenReturn("new-private-token");
        when(playlistTokenService.resolveToken(anyLong())).thenReturn("new-private-token");
        when(userService.getUserIfExistsById(1L)).thenReturn(owner);
        when(trackLikeRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(trackRepostRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(playlistLikeRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);
        when(playlistRepostRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);

        PatchPlaylistRequest request = new PatchPlaylistRequest(null, null, null, true, null);
        playlistService.patchPlaylist(1L, 10L, request);

        verify(playlistTokenService, times(1)).issueNewToken(any());
    }

    // ── getPlaylist ───────────────────────────────────────────────────────────
    @Test
    void getPlaylist_whenExists_returnsPlaylistSummaryResponse() {
        User user = user(1L);
        user.setTier(AccountTier.FREE);
        Playlist playlist = playlist(user);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(userService.getUserIfExistsById(1L)).thenReturn(user);
        when(trackLikeRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(trackRepostRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(playlistLikeRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);
        when(playlistRepostRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);
        when(playlistTokenService.resolveToken(anyLong())).thenReturn("token-123");

        PlaylistSummaryResponse response = playlistService.getPlaylist(10L, 1L);

        assertEquals(10L, response.id());
        assertEquals(1L, response.owner().id());
        assertEquals("token-123", response.secretToken());
    }

    @Test
    void getPlaylist_whenPrivateAndNotOwner_throwsNotFoundException() {
        User owner = user(2L);
        Playlist playlist = Playlist.builder()
                .id(10L).title("Private").slug("private").type(PlaylistType.PLAYLIST)
                .isPrivate(true).user(owner).tracks(new ArrayList<>()).genres(new ArrayList<>())
                .build();
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.getPlaylist(10L, 99L));
    }

    @Test
    void getPlaylistV2_returnsPagedTracks() {
        User user = user(1L);
        user.setTier(AccountTier.FREE);
        Playlist playlist = playlist(user);
        Pageable pageable = PageRequest.of(0, 20);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(userService.getUserIfExistsById(1L)).thenReturn(user);
        when(trackLikeRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(trackRepostRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(playlistLikeRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);
        when(playlistRepostRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);
        when(playlistTokenService.resolveToken(anyLong())).thenReturn("token-v2");

        PlaylistResponse response = playlistService.getPlaylistV2(10L, 1L, pageable);

        assertEquals(10L, response.id());
        assertNotNull(response.trackSummaryDto()); // Page<TrackSummaryDTO>
    }

    // ── addTrack ──────────────────────────────────────────────────────────────
    @Test
    void addTrack_whenValid_addsTrackAndUpdatesCounts() {
        User user = user(1L);
        user.setTier(AccountTier.FREE);
        Playlist playlist = playlist(user);
        Track track = track(100L, "Hip Hop", 180, user);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(100L)).thenReturn(Optional.of(track));
        when(playlistRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userService.getUserIfExistsById(1L)).thenReturn(user);
        when(trackLikeRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(trackRepostRepository.findTrackIdsByUserId(anyLong())).thenReturn(java.util.Collections.emptySet());
        when(playlistLikeRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);
        when(playlistRepostRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);
        when(playlistTokenService.resolveToken(anyLong())).thenReturn("tok");

        PlaylistSummaryResponse response = playlistService.addTrack(1L, 10L, 100L);

        assertEquals(1, response.trackCount());
        assertEquals(180, response.totalDurationSeconds());
        verify(playlistRepository).saveAndFlush(any());
    }

    @Test
    void addTrack_whenTrackNotFound_throwsNotFoundException() {
        User owner = user(10L);
        Playlist playlist = playlist(owner);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.addTrack(10L, 10L, 999L));
    }

    @Test
    void addTrack_whenTrackAlreadyInPlaylist_throwsException() {
        User user = user(1L);
        Playlist playlist = playlist(user);
        Track track = track(100L, "Pop", 200, user);
        playlist.getTracks().add(track);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(100L)).thenReturn(Optional.of(track));

        assertThrows(software.decibel.exceptions.custom.TrackAlreadyInPlaylistException.class,
                () -> playlistService.addTrack(1L, 10L, 100L));
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
        when(playlistRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playlistTokenService.resolveToken(anyLong())).thenReturn("tok");

        playlistService.removeTrack(1L, 10L, 100L);

        assertEquals(0, playlist.getTrackCount());
        assertEquals(0, playlist.getTotalDurationSeconds());
        assertTrue(playlist.getGenres().isEmpty());
        verify(playlistRepository).saveAndFlush(any());
    }

    @Test
    void removeTrack_whenTrackNotInPlaylist_throwsNotFoundException() {
        User user = user(1L);
        Playlist playlist = playlist(user);
        Track track = track(100L, "Pop", 200, user);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(100L)).thenReturn(Optional.of(track));

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.removeTrack(1L, 10L, 100L));
    }

    @Test
    void removeTrack_whenUserIsNotOwner_throwsForbidden() {
        User owner = user(10L);
        Playlist playlist = playlist(owner);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

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
