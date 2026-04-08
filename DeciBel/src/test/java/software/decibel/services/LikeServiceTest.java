package software.decibel.services;

import java.time.LocalDateTime;
import java.util.Collections;
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

import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.track.LikeResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistLike;
import software.decibel.entities.Track;
import software.decibel.entities.TrackLike;
import software.decibel.entities.User;
import software.decibel.enums.PlaylistType;
import software.decibel.mappers.LikeMapper;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.PlaylistLikeRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.user.UserService;
import software.decibel.utils.UserMappingUtility;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private TrackLikeRepository trackLikeRepository;
    @Mock
    private TrackRepository trackRepository;
    @Mock
    private UserService userService;
    @Mock
    private LikeMapper likeMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private PlaylistLikeRepository playlistLikeRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlaylistMapper playlistMapper;
    @Mock
    private UserMappingUtility userMappingUtility;
    @Mock
    private TrackRepostRepository trackRepostRepository;
    @InjectMocks
    private LikeService likeService;

    // ── TRACK LIKES ───────────────────────────────────────────────────────────
    @Test
    void likeTrack_success() {
        User user = new User();
        user.setId(1L);
        Track track = new Track();
        track.setId(10L);
        track.setLikeCount(5);

        try (MockedStatic<JwtService> mockedJwt = mockStatic(JwtService.class)) {
            mockedJwt.when(JwtService::getCurrentUserId).thenReturn(1L);
            when(userService.getUserIfExistsById(1L)).thenReturn(user);
            when(trackRepository.findById(10L)).thenReturn(Optional.of(track));
            when(trackLikeRepository.existsByUserAndTrack(any(), any())).thenReturn(false);
            when(likeMapper.toLikeResponse(anyBoolean())).thenReturn(new LikeResponse("liked", true));

            LikeResponse response = likeService.likeTrack(10L);

            assertNotNull(response);
            assertEquals(6, track.getLikeCount());
            verify(trackLikeRepository).save(any(TrackLike.class));
            verify(trackRepository).save(track);
        }
    }

    @Test
    void unlikeTrack_success() {
        User user = new User();
        user.setId(1L);
        Track track = new Track();
        track.setId(10L);
        track.setLikeCount(5);
        TrackLike existingLike = TrackLike.builder().user(user).track(track).build();

        try (MockedStatic<JwtService> mockedJwt = mockStatic(JwtService.class)) {
            mockedJwt.when(JwtService::getCurrentUserId).thenReturn(1L);
            when(userService.getUserIfExistsById(1L)).thenReturn(user);
            when(trackRepository.findById(10L)).thenReturn(Optional.of(track));
            when(trackLikeRepository.findByUserAndTrack(any(), any())).thenReturn(Optional.of(existingLike));

            likeService.unlikeTrack(10L);

            assertEquals(4, track.getLikeCount());
            verify(trackLikeRepository).delete(existingLike);
            verify(trackRepository).save(track);
        }
    }

    // ── PLAYLIST LIKES ────────────────────────────────────────────────────────
    @Test
    void likePlaylist_success() {
        User user = new User();
        user.setId(1L);
        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setLikeCount(5);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(playlistLikeRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);
        when(likeMapper.toLikeResponse(anyBoolean())).thenReturn(new LikeResponse("liked", true));

        LikeResponse response = likeService.likePlaylist(1L, 10L);

        assertNotNull(response);
        assertEquals(6, playlist.getLikeCount());
        verify(playlistLikeRepository).save(any(PlaylistLike.class));
    }

    @Test
    void likePlaylist_whenAlreadyLiked_throwsConflict() {
        User user = new User();
        user.setId(1L);
        Playlist playlist = new Playlist();
        playlist.setId(10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(playlistLikeRepository.existsByUserAndPlaylist(any(), any())).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, ()
                -> likeService.likePlaylist(1L, 10L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(playlistLikeRepository, never()).save(any());
    }

    @Test
    void unlikePlaylist_success() {
        User user = new User();
        user.setId(1L);
        Playlist playlist = new Playlist();
        playlist.setId(10L);
        playlist.setLikeCount(5);
        PlaylistLike existingLike = PlaylistLike.builder().user(user).playlist(playlist).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(playlistRepository.findById(10L)).thenReturn(Optional.of(playlist));
        when(playlistLikeRepository.findByUserAndPlaylist(any(), any())).thenReturn(Optional.of(existingLike));

        likeService.unlikePlaylist(1L, 10L);

        assertEquals(4, playlist.getLikeCount());
        verify(playlistLikeRepository).delete(existingLike);
    }

    @Test
    void getLikedPlaylists_success() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        Playlist playlist = new Playlist();
        playlist.setId(10L);

        Pageable pageable = PageRequest.of(0, 10);

        PlaylistResponse dummyResponse = new PlaylistResponse(
                10L,
                "Test Playlist",
                PlaylistType.PLAYLIST,
                false, // isLiked (boolean)
                "A playlist for testing", // description (String)
                false, // isPrivate (boolean)
                "http://example.com/cover.jpg", // coverArtUrl (String)
                300, // totalDurationSeconds (int)
                5, // trackCount (int)
                null, // owner (OwnerDto) - will be set in the mapper
                Collections.emptyList(), // genres (List<String>)
                LocalDateTime.now(), // createdAt (LocalDateTime)
                null // tracks (TrackPageResponse)
        );

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        when(playlistLikeRepository.findLikedPlaylistsByUserId(eq(1L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(playlist)));

        when(trackLikeRepository.findTrackIdsByUserId(1L)).thenReturn(Collections.emptySet());
        when(trackRepostRepository.findTrackIdsByUserId(1L)).thenReturn(Collections.emptySet());

        when(playlistMapper.toResponse(any(), any(), any(), any())).thenReturn(dummyResponse);

        try (MockedStatic<JwtService> mockedJwt = mockStatic(JwtService.class)) {
            mockedJwt.when(JwtService::getCurrentUserId).thenReturn(1L);

            Page<PlaylistResponse> result = likeService.getLikedPlaylists("testuser", pageable);

            // 4. ASSERTIONS
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(10L, result.getContent().get(0).id());
        }
    }
}
