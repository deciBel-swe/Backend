package software.decibel.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.engagement.RepostItemResponse;
import software.decibel.dtos.track.RepostResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistRepost;
import software.decibel.entities.Track;
import software.decibel.entities.TrackRepost;
import software.decibel.entities.User;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.RepostMapper;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistRepostRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.user.UserService;
import software.decibel.utils.UserMappingUtility;

@ExtendWith(MockitoExtension.class)
class RepostServiceTest {

    @Mock
    private TrackRepostRepository trackRepostRepository;
    @Mock
    private TrackRepository trackRepository;
    @Mock
    private UserService userService;
    @Mock
    private RepostMapper repostMapper;
    @Mock
    private PlaylistRepostRepository playlistRepostRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMappingUtility userMappingUtility;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private RepostService repostService;

    // ── TRACK REPOSTS ─────────────────────────────────────────────────────────
    @Test
    void repostTrack_success() {
        User user = new User();
        user.setId(1L);
        Track track = new Track();
        track.setId(10L);
        track.setVisibility(Visibility.PUBLIC);
        track.setRepostCount(0);

        try (MockedStatic<JwtService> mockedJwt = mockStatic(JwtService.class)) {
            mockedJwt.when(JwtService::getCurrentUserId).thenReturn(1L);
            when(userService.getUserIfExistsById(1L)).thenReturn(user);
            when(trackRepository.findById(10L)).thenReturn(Optional.of(track));
            when(trackRepostRepository.existsByUserAndTrack(any(User.class), any(Track.class))).thenReturn(false);
            when(repostMapper.toRepostResponse(anyBoolean())).thenReturn(new RepostResponse("reposted", true));

            RepostResponse response = repostService.repostTrack(10L);

            assertNotNull(response);
            assertEquals(1, track.getRepostCount());
            verify(trackRepostRepository).save(any(TrackRepost.class));
            verify(trackRepository).save(track);
        }
    }

    @Test
    void repostTrack_whenPrivate_throwsForbidden() {
        User user = new User();
        user.setId(1L);
        Track track = new Track();
        track.setId(10L);
        track.setVisibility(Visibility.PRIVATE);

        try (MockedStatic<JwtService> mockedJwt = mockStatic(JwtService.class)) {
            mockedJwt.when(JwtService::getCurrentUserId).thenReturn(1L);
            when(userService.getUserIfExistsById(1L)).thenReturn(user);
            when(trackRepository.findById(10L)).thenReturn(Optional.of(track));
            when(trackRepostRepository.existsByUserAndTrack(any(), any())).thenReturn(false);

            ResponseStatusException exception = assertThrows(ResponseStatusException.class, ()
                    -> repostService.repostTrack(10L)
            );

            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        }
    }

    @Test
    void removeRepostTrack_success() {
        User user = new User();
        user.setId(1L);
        Track track = new Track();
        track.setId(10L);
        track.setRepostCount(1);
        TrackRepost existingRepost = TrackRepost.builder().user(user).track(track).build();

        try (MockedStatic<JwtService> mockedJwt = mockStatic(JwtService.class)) {
            mockedJwt.when(JwtService::getCurrentUserId).thenReturn(1L);
            when(userService.getUserIfExistsById(1L)).thenReturn(user);
            when(trackRepository.findById(10L)).thenReturn(Optional.of(track));
            when(trackRepostRepository.findByUserAndTrack(any(), any())).thenReturn(Optional.of(existingRepost));

            repostService.removeRepost(10L);

            assertEquals(0, track.getRepostCount());
            verify(trackRepostRepository).delete(existingRepost);
            verify(trackRepository).save(track);
        }
    }

    // ── PLAYLIST REPOSTS ──────────────────────────────────────────────────────
    @Test
    void repostPlaylist_success() {
        User user = new User();
        user.setId(1L);
        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setRepostCount(2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(playlistRepostRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);
        when(repostMapper.toRepostResponse(anyBoolean())).thenReturn(new RepostResponse("reposted", true));

        RepostResponse response = repostService.repostPlaylist(1L, 10L);

        assertNotNull(response);
        assertEquals(3, playlist.getRepostCount());
        verify(playlistRepostRepository).save(any(PlaylistRepost.class));
    }

    @Test
    void unrepostPlaylist_success() {
        User user = new User();
        user.setId(1L);
        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setRepostCount(2);
        PlaylistRepost existingRepost = PlaylistRepost.builder().user(user).playlist(playlist).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(playlistRepostRepository.findByUserAndPlaylist(any(), any())).thenReturn(Optional.of(existingRepost));

        repostService.unrepostPlaylist(1L, 10L);

        assertEquals(1, playlist.getRepostCount());
        verify(playlistRepostRepository).delete(existingRepost);
    }

    // ── MIXED FEED & FETCHING USERS ───────────────────────────────────────────
    @Test
    void getUserReposts_success() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setTitle("P1");
        Track track = new Track();
        track.setId(100L);
        track.setTitle("T1");
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        PlaylistRepost pr = PlaylistRepost.builder().playlist(playlist).repostedAt(LocalDateTime.now().minusDays(1)).build();
        TrackRepost tr = TrackRepost.builder().track(track).repostedAt(LocalDateTime.now()).build();

        when(playlistRepostRepository.findByUser(eq(user), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(pr)));
        when(trackRepostRepository.findByUser(eq(user), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(tr)));

        Page<RepostItemResponse> result = repostService.getUserReposts("testuser", pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals("TRACK", result.getContent().get(0).type()); // Track is newer, so it should be first
        assertEquals("PLAYLIST", result.getContent().get(1).type());
    }

    @Test
    void getPlaylistReposters_notFound_throwsException() {
        Pageable pageable = PageRequest.of(0, 10);
        when(playlistRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, ()
                -> repostService.getPlaylistReposters(99L, pageable)
        );

        verify(playlistRepostRepository, never()).findUsersByPlaylistId(any(), any());
    }
}
