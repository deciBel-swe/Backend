package software.decibel.controllers;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import software.decibel.dtos.track.responses.TrackPageResponse;
import software.decibel.services.track.TrackService;

@ExtendWith(MockitoExtension.class)
class ExploreControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TrackService trackService;

    @InjectMocks
    private ExploreController exploreController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(exploreController).build();
    }

    @Test
    void getTrendingTracks_returnsOk() throws Exception {
        TrackPageResponse response = new TrackPageResponse(
                List.of(), 0, 10, 0, 0, true
        );

        when(trackService.getTrendingTracks(anyInt(), anyInt())).thenReturn(response);

        mockMvc.perform(get("/explore/trending")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(10));
    }
}
