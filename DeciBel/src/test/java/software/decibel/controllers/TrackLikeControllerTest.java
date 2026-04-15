package software.decibel.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.decibel.controllers.Track.TrackLikeController;
import software.decibel.dtos.track.responses.LikeResponse;
import software.decibel.services.engagement.LikeService;

@ExtendWith(MockitoExtension.class)
class TrackLikeControllerTest {

    @Mock
    private LikeService likeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TrackLikeController(likeService)).build();
    }

    @Test
    void likeTrack_whenRequestIsValid_returnsOkResponse() throws Exception {
        when(likeService.likeTrack(5L)).thenReturn(new LikeResponse("Track liked", true));

        mockMvc.perform(post("/tracks/5/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Track liked"))
                .andExpect(jsonPath("$.isLiked").value(true));

        verify(likeService).likeTrack(5L);
    }

    @Test
    void unlikeTrack_whenRequestIsValid_returnsOkResponse() throws Exception {
        when(likeService.unlikeTrack(5L)).thenReturn(new LikeResponse("Like removed", false));

        mockMvc.perform(delete("/tracks/5/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Like removed"))
                .andExpect(jsonPath("$.isLiked").value(false));

        verify(likeService).unlikeTrack(5L);
    }
}
