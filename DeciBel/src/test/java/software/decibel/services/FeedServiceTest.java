package software.decibel.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import software.decibel.dtos.discovery.FeedPageResponse;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.track.responses.TrackResponse;
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

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private TrackRepository trackRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private software.decibel.repositories.TrackRepostRepository trackRepostRepository;
    @Mock
    private software.decibel.repositories.PlaylistRepostRepository playlistRepostRepository;
    @Mock
    private LikeService likeService;
    @Mock
    private RepostService repostService;
    @Mock
    private TrackMapper trackMapper;
    @Mock
    private PlaylistMapper playlistMapper;
    @Mock
    private software.decibel.mappers.UserMapper userMapper;

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

        User user2 = new User();
        user2.setId(2L);
        Track track = new Track();
        track.setId(10L);
        software.decibel.entities.TrackRepost trackRepost = software.decibel.entities.TrackRepost.builder()
                .track(track)
                .user(user2)
                .repostedAt(LocalDateTime.now().minusDays(1))
                .build();
        Page<software.decibel.entities.TrackRepost> trackRepostsPage = new PageImpl<>(List.of(trackRepost), pageable, 1);
        when(trackRepostRepository.findByUserIdIn(eq(followingIds), any(Pageable.class)))
                .thenReturn(trackRepostsPage);

        Playlist playlist = new Playlist();
        playlist.setId(20L);
        software.decibel.entities.PlaylistRepost playlistRepost = software.decibel.entities.PlaylistRepost.builder()
                .playlist(playlist)
                .user(user2)
                .repostedAt(LocalDateTime.now())
                .build();
        Page<software.decibel.entities.PlaylistRepost> playlistRepostsPage = new PageImpl<>(List.of(playlistRepost), pageable, 1);
        when(playlistRepostRepository.findByUserIdIn(eq(followingIds), any(Pageable.class)))
                .thenReturn(playlistRepostsPage);

        when(likeService.getLikedTrackIds(1L)).thenReturn(Set.of());
        when(repostService.getRepostedTrackIds(1L)).thenReturn(Set.of());

        TrackResponse trackResponse = new TrackResponse(
                10L,
                "Track Title",
                null,
                "url",
                "preview",
                "cover",
                "waveform",
                "genre",
                false,
                false,
                null,
                LocalDate.now().minusDays(1),
                0,
                0,
                0,
                0,
                0,
                false,
                120,
                LocalDate.now().minusDays(1),
                "desc",
                "token",
                software.decibel.enums.TrackAccess.PLAYABLE,
                "track-slug"
        );
        when(trackMapper.toTrackResponse(any(), any(), any(), any())).thenReturn(trackResponse);

        PlaylistResponse playlistResponse = new PlaylistResponse(
                20L, "Playlist Title", software.decibel.enums.PlaylistType.PLAYLIST, false, "desc", false, "cover",
                "playlist-slug", 0, 0, null, java.util.Collections.emptyList(), LocalDateTime.now(), null, null
        );
        when(playlistMapper.toResponse(playlist)).thenReturn(playlistResponse);

        // FIX 1: Updated the mocked method name and returned the fully populated UserSummaryDTO
        when(userMapper.toUserSummaryDto(any())).thenReturn(
                new software.decibel.dtos.user.UserSummaryDTO(2L, "user2", "User Two", "avatar", false, 0, 0)
        );

        FeedPageResponse response = feedService.getFeed(currentUser, pageable);

        assertNotNull(response);
        assertEquals(2, response.content().size());

        // FIX 2: Updated the assertions to match the new FeedItemDto type strings
        assertEquals("PLAYLIST_POSTED", response.content().get(0).type()); // Playlist is more recent
        assertEquals("TRACK_POSTED", response.content().get(1).type());

        // Optional: You can also verify the inner resource type works correctly
        assertEquals("PLAYLIST", response.content().get(0).resource().type());
        assertEquals("TRACK", response.content().get(1).resource().type());

        assertEquals(2, response.totalElements());
    }
}
