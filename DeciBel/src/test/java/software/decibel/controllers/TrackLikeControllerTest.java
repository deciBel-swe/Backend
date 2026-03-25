package software.decibel.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.decibel.controllers.Track.TrackLikeController;
import software.decibel.dtos.track.LikeResponse;
import software.decibel.services.track.TrackService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TrackLikeControllerTest {

    @Mock
    private TrackService trackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TrackLikeController(trackService)).build();
    }

    @Test
    void likeTrack_whenRequestIsValid_returnsOkResponse() throws Exception {
        when(trackService.likeTrack(5L)).thenReturn(new LikeResponse("Track liked", true));

        mockMvc.perform(post("/tracks/5/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Track liked"))
                .andExpect(jsonPath("$.isLiked").value(true));

        verify(trackService).likeTrack(5L);
    }

    @Test
    void unlikeTrack_whenRequestIsValid_returnsOkResponse() throws Exception {
        when(trackService.unlikeTrack(5L)).thenReturn(new LikeResponse("Like removed", false));

        mockMvc.perform(delete("/tracks/5/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Like removed"))
                .andExpect(jsonPath("$.isLiked").value(false));

        verify(trackService).unlikeTrack(5L);
    }
}
