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
import software.decibel.controllers.Track.TrackRepostController;
import software.decibel.dtos.track.responses.RepostResponse;
import software.decibel.services.engagement.RepostService;

@ExtendWith(MockitoExtension.class)
class TrackRepostControllerTest {

    @Mock
    private RepostService repostService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TrackRepostController(repostService)).build();
    }

    @Test
    void repostTrack_whenRequestIsValid_returnsOkResponse() throws Exception {
        when(repostService.repostTrack(5L)).thenReturn(new RepostResponse("Track reposted", true));

        mockMvc.perform(post("/tracks/5/repost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Track reposted"))
                .andExpect(jsonPath("$.isReposted").value(true));

        verify(repostService).repostTrack(5L);
    }

    @Test
    void removeRepost_whenRequestIsValid_returnsOkResponse() throws Exception {
        when(repostService.removeRepost(5L)).thenReturn(new RepostResponse("Repost removed", false));

        mockMvc.perform(delete("/tracks/5/repost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Repost removed"))
                .andExpect(jsonPath("$.isReposted").value(false));

        verify(repostService).removeRepost(5L);
    }
}
