package software.decibel.controllers;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistSummaryResponse;
import software.decibel.dtos.user.UserSummaryDTO;
import software.decibel.enums.PlaylistType;
import software.decibel.services.playlist.PlaylistService;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.JwtService;

@ExtendWith(MockitoExtension.class)
class PlaylistControllerTest {

    @Mock
    private PlaylistService playlistService;

    @Mock
    private LikeService likeService;

    @Mock
    private RepostService repostService;

    @InjectMocks
    private PlaylistController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private MockedStatic<JwtService> mockedJwt;

    @BeforeEach
    void setUp() {
        mockedJwt = mockStatic(JwtService.class);
        mockedJwt.when(JwtService::getCurrentUserId).thenReturn(1L);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        // Always close the static mock after each test to prevent memory leaks
        if (mockedJwt != null) {
            mockedJwt.close();
        }
    }

    // ── Create Playlist ────────────────────────────────────────────────────────
    @Test
    void createPlaylist_whenRequestIsValid_returnsCreated() throws Exception {
        when(playlistService.createPlaylist(any(), any()))
                .thenReturn(playlistSummaryResponse());

        mockMvc.perform(multipart("/playlists")
                .param("title", "My Playlist")
                .param("type", "PLAYLIST")
                .param("isPrivate", "false"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My Playlist"))
                .andExpect(jsonPath("$.type").value("PLAYLIST"))
                .andExpect(jsonPath("$.trackCount").value(10));
    }

    @Test
    void createPlaylistV2_whenRequestIsValid_returnsCreated() throws Exception {
        when(playlistService.createPlaylistV2(any(), any(), any()))
                .thenReturn(playlistResponse());

        mockMvc.perform(multipart("/playlists/v2")
                .param("title", "My Playlist")
                .param("type", "PLAYLIST")
                .param("isPrivate", "false"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My Playlist"))
                .andExpect(jsonPath("$.type").value("PLAYLIST"));
    }

    @Test
    void createPlaylist_whenTitleIsMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/playlists")
                .param("type", "PLAYLIST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPlaylist_whenTypeIsMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/playlists")
                .param("title", "My Playlist"))
                .andExpect(status().isBadRequest());
    }

    // ── Patch Playlist ────────────────────────────────────────────────────────
    @Test
    void patchPlaylist_whenRequestIsValid_returnsOk() throws Exception {
        when(playlistService.patchPlaylist(any(), eq(10L), any()))
                .thenReturn(playlistSummaryResponse());

        mockMvc.perform(multipart("/playlists/10")
                .param("title", "Updated Title")
                .with(req -> {
                    req.setMethod("PATCH");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void patchPlaylistV2_whenRequestIsValid_returnsOk() throws Exception {
        when(playlistService.patchPlaylistV2(any(), eq(10L), any(), any()))
                .thenReturn(playlistResponse());

        mockMvc.perform(multipart("/playlists/10/v2")
                .param("title", "Updated Title")
                .with(req -> {
                    req.setMethod("PATCH");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── Get Playlist ──────────────────────────────────────────────────────────
    @Test
    void getPlaylist_whenExists_returnsOk() throws Exception {
        when(playlistService.getPlaylist(eq(10L), any())).thenReturn(playlistSummaryResponse());

        mockMvc.perform(get("/playlists/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("My Playlist"));
    }

    @Test
    void getPlaylistV2_whenExists_returnsOk() throws Exception {
        when(playlistService.getPlaylistV2(eq(10L), any(), any(Pageable.class))).thenReturn(playlistResponse());

        mockMvc.perform(get("/playlists/10/v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("My Playlist"));
    }

    // ── Add Track ─────────────────────────────────────────────────────────────
    @Test
    void addTrack_whenValid_returnsOk() throws Exception {
        PlaylistSummaryResponse response = playlistSummaryResponse();

        when(playlistService.addTrack(any(), eq(10L), eq(100L))).thenReturn(response);

        mockMvc.perform(post("/playlists/10/tracks")
                .param("trackId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackCount").value(10));

        verify(playlistService).addTrack(any(), eq(10L), eq(100L));
    }

    @Test
    void addTrackV2_whenValid_returnsOk() throws Exception {
        PlaylistResponse response = playlistResponse();

        when(playlistService.addTrackV2(any(), eq(10L), eq(100L), any())).thenReturn(response);

        mockMvc.perform(post("/playlists/10/tracks/v2")
                .param("trackId", "100"))
                .andExpect(status().isOk());

        verify(playlistService).addTrackV2(any(), eq(10L), eq(100L), any());
    }

    // ── Remove Track ──────────────────────────────────────────────────────────
    @Test
    void removeTrack_whenValid_returnsOk() throws Exception {
        mockMvc.perform(delete("/playlists/10/tracks/100"))
                .andExpect(status().isNoContent());

        verify(playlistService).removeTrack(any(), eq(10L), eq(100L));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private PlaylistSummaryResponse playlistSummaryResponse() {
        UserSummaryDTO owner = new UserSummaryDTO(
                2L,
                "testuser",
                "Test User",
                null,
                false,
                0,
                10
        );
        return new PlaylistSummaryResponse(
                1L, // id
                "My Playlist", // title
                PlaylistType.PLAYLIST, // type
                false, // isLiked
                false, // isReposted
                "Description", // description
                false, // isPrivate
                "cover-image-url", // coverArtUrl
                "my-playlist-slug", // playlistSlug
                3600, // totalDurationSeconds
                10, // trackCount
                owner, // owner
                List.of("Rock"), // genres
                LocalDateTime.now(), // createdAt
                List.of(), // trackSummaryDto (List<TrackSummaryDTO>)
                "waveform-url", // firstTrackWaveformUrl
                "secret-token" // secretToken
        );
    }

    private PlaylistResponse playlistResponse() {
        UserSummaryDTO owner = new UserSummaryDTO(2L, "testuser", "Test User", null, false, 0, 10);
        return new PlaylistResponse(
                1L, "My Playlist", PlaylistType.PLAYLIST, false, false, "Description", false,
                "cover-image-url", "my-playlist-slug", 3600, 10, owner,
                List.of("Rock"), LocalDateTime.now(),
                null, // <--- CHANGE THIS FROM org.springframework.data.domain.Page.empty() TO null
                "waveform-url", "secret-token"
        );
    }
}
