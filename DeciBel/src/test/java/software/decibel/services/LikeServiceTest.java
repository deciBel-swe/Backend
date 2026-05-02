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
import static org.mockito.ArgumentMatchers.anyLong;
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

import software.decibel.dtos.track.responses.LikeResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistLike;
import software.decibel.entities.Track;
import software.decibel.entities.TrackLike;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.mappers.LikeMapper;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.repositories.PlaylistLikeRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistTokenRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.playlist.PlaylistService;
import software.decibel.services.user.UserService;

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
    private BlockService blockService;
    @Mock
    private PlaylistLikeRepository playlistLikeRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private PlaylistMapper playlistMapper;
    @Mock
    private PlaylistTokenRepository playlistTokenRepository;
    @Mock
    private PlaylistService playlistService;
    @Mock
    private TrackRepostRepository trackRepostRepository;
    @Mock
    private software.decibel.services.playlist.PlaylistTokenService playlistTokenService;
    @Mock
    private software.decibel.repositories.PlaylistRepostRepository playlistRepostRepository;
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

        when(userService.getUserIfExistsById(1L)).thenReturn(user);
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

        when(userService.getUserIfExistsById(1L)).thenReturn(user);
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

        when(userService.getUserIfExistsById(1L)).thenReturn(user);
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
        user.setTier(AccountTier.FREE);
        user.setUsername("testuser");

        Playlist playlist = new Playlist();
        playlist.setId(10L);

        Pageable pageable = PageRequest.of(0, 10);

        software.decibel.dtos.playlist.PlaylistSummaryResponse dummySummaryResponse = new software.decibel.dtos.playlist.PlaylistSummaryResponse(
                10L,
                "Test Playlist",
                software.decibel.enums.PlaylistType.PLAYLIST,
                true, // isLiked 
                false, // isReposted
                "A playlist for testing", // description 
                false, // isPrivate 
                "http://example.com/cover.jpg", // coverArtUrl 
                "test-playlist-slug", // playlistSlug 
                300, // totalDurationSeconds 
                5, // trackCount 
                null, // owner 
                java.util.Collections.emptyList(), // genres 
                java.time.LocalDateTime.now(), // createdAt 
                java.util.Collections.emptyList(), // trackSummaryDto
                "waveform-url", // firstTrackWaveformUrl
                "secret-token" // secretToken
        );

        when(userService.getUserIfExistsByUsername("testuser")).thenReturn(user);
        when(userService.getUserIfExistsById(1L)).thenReturn(user);
        org.mockito.Mockito.lenient().when(blockService.isBlockRelationshipActive(any(), any())).thenReturn(false);

        // Keep ONLY this mock for the repository (removed the duplicate above it)
        when(playlistLikeRepository.findPlaylistsByUserId(eq(1L), eq(pageable)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(playlist)));

        org.mockito.Mockito.lenient().when(trackLikeRepository.findTrackIdsByUserId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(java.util.Collections.emptySet());
        org.mockito.Mockito.lenient().when(trackRepostRepository.findTrackIdsByUserId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(java.util.Collections.emptySet());
        org.mockito.Mockito.lenient().when(playlistRepostRepository.existsByUserAndPlaylist(any(), any())).thenReturn(false);

        org.mockito.Mockito.lenient().when(playlistService.resolveSecretTokenForUser(any())).thenReturn("secret-token");
        org.mockito.Mockito.lenient().when(playlistRepostRepository.findPlaylistIdsByUserId(any())).thenReturn(java.util.Collections.emptySet());

        org.mockito.Mockito.lenient().when(playlistMapper.toSummaryResponse(any(Playlist.class), any()))
                .thenReturn(dummySummaryResponse);
        org.mockito.Mockito.lenient().when(playlistMapper.toSummaryResponse(any(Playlist.class), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any()))
                .thenReturn(dummySummaryResponse);

        try (org.mockito.MockedStatic<software.decibel.services.JwtService> mockedJwt = org.mockito.Mockito.mockStatic(software.decibel.services.JwtService.class)) {
            mockedJwt.when(software.decibel.services.JwtService::getCurrentUserId).thenReturn(1L);

            org.springframework.data.domain.Page<software.decibel.dtos.playlist.PlaylistSummaryResponse> result = likeService.getLikedPlaylists("testuser", pageable);

            //ASSERTIONS
            org.junit.jupiter.api.Assertions.assertNotNull(result);
            org.junit.jupiter.api.Assertions.assertEquals(1, result.getTotalElements());
            org.junit.jupiter.api.Assertions.assertEquals(10L, result.getContent().get(0).id());
        }
    }
}
