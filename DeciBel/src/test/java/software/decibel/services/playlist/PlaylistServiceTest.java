package software.decibel.services.playlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.PlaylistType;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.user.UserService;
import software.decibel.utils.FileUtilityAzure;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private TrackRepository trackRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileUtilityAzure fileUtilityAzure;

    @Mock
    private UserService userService;

    @Mock
    private TrackMapper trackMapper;

    private PlaylistMapper playlistMapper;

    @InjectMocks
    private PlaylistService playlistService;

    @BeforeEach
    void setUp() {
        // 1. Manually create the real mapper, passing in the mocked TrackMapper
        playlistMapper = new PlaylistMapper(trackMapper);

        // 2. Force Mockito/Spring to put this real mapper into your service
        ReflectionTestUtils.setField(playlistService, "playlistMapper", playlistMapper);
    }

    // ── createPlaylist ────────────────────────────────────────────────────────
    @Test
    void createPlaylist_whenRequestIsValid_returnsPlaylistResponse() {
        User user = user();
        when(userService.getUserIfExistsById(anyLong())).thenReturn(user);

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                "My Playlist", "desc", PlaylistType.PLAYLIST, false, null);

        when(playlistRepository.existsBySlug(anyString())).thenReturn(false);
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(inv -> {
            Playlist p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        PlaylistResponse response = playlistService.createPlaylist(1L, request);

        assertEquals("My Playlist", response.title());
        assertEquals(PlaylistType.PLAYLIST, response.type());
        assertEquals(0, response.trackCount());
        assertFalse(response.isPrivate());
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
        User user = user();
        var mockFile = mock(org.springframework.web.multipart.MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);

        CreatePlaylistRequest request = new CreatePlaylistRequest(
                "My Album", "desc", PlaylistType.ALBUM, true, mockFile);

        when(playlistRepository.existsBySlug(anyString())).thenReturn(false);
        when(fileUtilityAzure.saveFile(any(), any())).thenReturn("https://azure.com/cover.jpg");
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(inv -> {
            Playlist p = inv.getArgument(0);
            p.setId(11L);
            return p;
        });

        PlaylistResponse response = playlistService.createPlaylist(1L, request);

        assertEquals("https://azure.com/cover.jpg", response.coverArtUrl());
        verify(fileUtilityAzure).saveFile(any(), any());
    }

    // ── patchPlaylist ─────────────────────────────────────────────────────────
    @Test
    void patchPlaylist_whenUserIsNotOwner_throwsForbidden() {
        User owner = user();
        Playlist playlist = playlist(owner);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        PatchPlaylistRequest request = new PatchPlaylistRequest("New Title", null, null, null, null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> playlistService.patchPlaylist(99L, 10L, request)); // wrong userId

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(playlistRepository, never()).save(any());
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
        User user = user();
        Playlist playlist = playlist(user);
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        PlaylistResponse response = playlistService.getPlaylist(10L, PageRequest.of(0, 20));

        assertEquals(10L, response.id());
        assertEquals(1L, response.userId());
    }

    @Test
    void getPlaylist_whenNotFound_throwsNotFoundException() {
        when(playlistRepository.findById(99L)).thenReturn(Optional.empty());

        // ADDED PageRequest.of(0, 20) to match the new method signature!
        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.getPlaylist(99L, PageRequest.of(0, 20)));
    }

    // ── addTrack ──────────────────────────────────────────────────────────────
    @Test
    void addTrack_whenValid_addsTrackAndUpdatesCounts() {
        User user = user();
        Playlist playlist = playlist(user);
        Track track = track(100L, "Hip Hop", 180);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(100L)).thenReturn(Optional.of(track));
        when(playlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlaylistResponse response = playlistService.addTrack(1L, 10L, 100L);

        assertEquals(1, response.trackCount());
        assertEquals(180, response.totalDurationSeconds());
        assertTrue(response.genres().contains("Hip Hop"));
        verify(playlistRepository).save(any());
    }

    @Test
    void addTrack_whenUserIsNotOwner_throwsForbidden() {
        User owner = user();
        Playlist playlist = playlist(owner);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> playlistService.addTrack(99L, 10L, 100L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void addTrack_whenTrackNotFound_throwsNotFoundException() {
        User user = user();
        Playlist playlist = playlist(user);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.addTrack(1L, 10L, 999L));
    }

    // ── removeTrack ───────────────────────────────────────────────────────────
    @Test
    void removeTrack_whenValid_removesTrackAndUpdatesCounts() {
        User user = user();
        Track track = track(100L, "Hip Hop", 180);
        Playlist playlist = playlist(user);
        playlist.getTracks().add(track);
        playlist.setTrackCount(1);
        playlist.setTotalDurationSeconds(180);
        playlist.setGenres(new ArrayList<>(List.of("Hip Hop")));

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(100L)).thenReturn(Optional.of(track));
        when(playlistRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlaylistResponse response = playlistService.removeTrack(1L, 10L, 100L);

        assertEquals(0, response.trackCount());
        assertEquals(0, response.totalDurationSeconds());
        assertTrue(response.genres().isEmpty());
        verify(playlistRepository).save(any());
    }

    @Test
    void removeTrack_whenTrackNotInPlaylist_throwsNotFoundException() {
        User user = user();
        Playlist playlist = playlist(user);
        Track track = track(100L, "Pop", 200);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(trackRepository.findById(100L)).thenReturn(Optional.of(track));

        assertThrows(ResourceNotFoundException.class,
                () -> playlistService.removeTrack(1L, 10L, 100L));
    }

    @Test
    void removeTrack_whenUserIsNotOwner_throwsForbidden() {
        User owner = user();
        Playlist playlist = playlist(owner);

        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> playlistService.removeTrack(99L, 10L, 100L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private User user() {
        return User.builder().id(1L).username("testuser").build();
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

    private Track track(Long id, String genre, int durationSeconds) {
        Track track = new Track();
        track.setId(id);
        track.setGenre(genre);
        track.setDurationSeconds(durationSeconds);
        return track;
    }
}
