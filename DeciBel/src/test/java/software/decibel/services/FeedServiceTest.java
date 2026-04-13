package software.decibel.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import software.decibel.dtos.discovery.FeedPageResponse;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.track.TrackResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private TrackRepository trackRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private LikeService likeService;
    @Mock
    private RepostService repostService;
    @Mock
    private TrackMapper trackMapper;
    @Mock
    private PlaylistMapper playlistMapper;

    @InjectMocks
    private FeedService feedService;

    private User currentUser;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void getFeed_noFollowing_returnsEmptyResponse() {
        when(followRepository.findFollowingIdsByFollowerId(1L)).thenReturn(Collections.emptyList());

        FeedPageResponse response = feedService.getFeed(currentUser, pageable);

        assertNotNull(response);
        assertTrue(response.content().isEmpty());
        assertEquals(0, response.totalElements());
    }

    @Test
    void getFeed_withContent_returnsSortedInterleavedResponse() {
        List<Long> followingIds = List.of(2L, 3L);
        when(followRepository.findFollowingIdsByFollowerId(1L)).thenReturn(followingIds);

        Track track = new Track();
        track.setId(10L);
        Page<Track> tracksPage = new PageImpl<>(List.of(track), pageable, 1);
        when(trackRepository.findByUploaderIdInAndVisibilityPublicAndPublishedTrue(eq(followingIds), any(Pageable.class)))
                .thenReturn(tracksPage);

        Playlist playlist = new Playlist();
        playlist.setId(20L);
        Page<Playlist> playlistsPage = new PageImpl<>(List.of(playlist), pageable, 1);
        when(playlistRepository.findByUserIdInAndIsPrivateFalse(eq(followingIds), any(Pageable.class)))
                .thenReturn(playlistsPage);

        when(likeService.getLikedTrackIds(1L)).thenReturn(Set.of());
        when(repostService.getRepostedTrackIds(1L)).thenReturn(Set.of());

        TrackResponse trackResponse = new TrackResponse(
                10L, "Track Title", null, "url", "cover", "waveform", "genre",
                false, false, null, LocalDate.now().minusDays(1), 0, 0, 0,
                false, 120, LocalDate.now().minusDays(1), "desc", 0L, 0L,
                "FULL", "token", "preview"
        );
        when(trackMapper.toTrackResponse(eq(track), any(), any())).thenReturn(trackResponse);

        PlaylistResponse playlistResponse = new PlaylistResponse(
                20L, "Playlist Title", null, false, "desc", false, "cover",
                0, 0, null, null, LocalDateTime.now(), null
        );
        when(playlistMapper.toResponse(playlist)).thenReturn(playlistResponse);

        FeedPageResponse response = feedService.getFeed(currentUser, pageable);

        assertNotNull(response);
        assertEquals(2, response.content().size());
        assertEquals("PLAYLIST", response.content().get(0).type()); // Playlist is more recent (now vs yesterday)
        assertEquals("TRACK", response.content().get(1).type());
        assertEquals(2, response.totalElements());
    }
}
