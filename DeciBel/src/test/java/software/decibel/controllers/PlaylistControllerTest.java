package software.decibel.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.enums.PlaylistType;
import software.decibel.services.playlist.PlaylistService;

// Make sure to import your UserPrincipal!
import software.decibel.dtos.auth.UserPrincipal;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PlaylistControllerTest {

    @Mock
    private PlaylistService playlistService;

    // Security Mocks
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;
    @Mock
    private UserPrincipal userPrincipal; // <-- Added this to fix the ClassCastException

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        PlaylistController controller = new PlaylistController(playlistService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        // Use lenient() to fix the UnnecessaryStubbingException
        // Return the mocked UserPrincipal to fix the ClassCastException
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(userPrincipal);

        // Note: If JwtService.getCurrentUserId() calls a method like userPrincipal.getId(), 
        // uncomment the following line and adjust the method name if necessary:
        // lenient().when(userPrincipal.getId()).thenReturn(1L);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPlaylist_whenRequestIsValid_returnsCreated() throws Exception {
        when(playlistService.createPlaylist(any(), any()))
                .thenReturn(playlistResponse());

        mockMvc.perform(multipart("/playlists")
                .param("title", "My Playlist")
                .param("type", "PLAYLIST")
                .param("isPrivate", "false"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My Playlist"))
                .andExpect(jsonPath("$.slug").value("my-playlist"))
                .andExpect(jsonPath("$.type").value("PLAYLIST"))
                .andExpect(jsonPath("$.trackCount").value(0));
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

    @Test
    void patchPlaylist_whenRequestIsValid_returnsOk() throws Exception {
        when(playlistService.patchPlaylist(any(), anyLong(), any()))
                .thenReturn(playlistResponse());

        mockMvc.perform(multipart("/playlists/10")
                .param("title", "Updated Title")
                .with(req -> {
                    req.setMethod("PATCH");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void getPlaylist_whenExists_returnsOk() throws Exception {
        when(playlistService.getPlaylist(10L)).thenReturn(playlistResponse());

        mockMvc.perform(get("/playlists/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("My Playlist"))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void addTrack_whenValid_returnsOk() throws Exception {
        PlaylistResponse response = new PlaylistResponse(
                10L, "My Playlist", "my-playlist", null,
                PlaylistType.PLAYLIST, false, null, 1, 180,
                List.of("Hip Hop"), 1L, List.of(100L), LocalDateTime.now());

        when(playlistService.addTrack(any(), eq(10L), eq(100L))).thenReturn(response);

        mockMvc.perform(post("/playlists/10/tracks")
                .param("trackId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackCount").value(1))
                .andExpect(jsonPath("$.trackIds[0]").value(100));

        verify(playlistService).addTrack(any(), eq(10L), eq(100L));
    }

    @Test
    void removeTrack_whenValid_returnsOk() throws Exception {
        when(playlistService.removeTrack(any(), eq(10L), eq(100L)))
                .thenReturn(playlistResponse());

        mockMvc.perform(delete("/playlists/10/tracks/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackCount").value(0));

        verify(playlistService).removeTrack(any(), eq(10L), eq(100L));
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private PlaylistResponse playlistResponse() {
        return new PlaylistResponse(
                10L, "My Playlist", "my-playlist", "desc",
                PlaylistType.PLAYLIST, false, null, 0, 0,
                List.of(), 1L, List.of(), LocalDateTime.now());
    }
}
