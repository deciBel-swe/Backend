package software.decibel.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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

import software.decibel.controllers.Track.TrackController;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.exceptions.GlobalExceptionHandler;
import software.decibel.exceptions.custom.CooldownActiveException;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.services.track.TrackService;

@ExtendWith(MockitoExtension.class)
class TrackControllerTest {

    @Mock
    private TrackService trackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TrackController(trackService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void playTrack_whenRequestIsValid_returnsOk() throws Exception {
        when(trackService.recordTrackPlay(5L)).thenReturn(new MessageResponse("Play recorded"));

        mockMvc.perform(post("/tracks/5/play")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Play recorded"));

        verify(trackService).recordTrackPlay(5L);
    }

    @Test
    void playTrack_whenTrackIdIsInvalidType_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tracks/not-a-number/play")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(trackService);
    }

    @Test
    void playTrack_whenTrackDoesNotExist_returnsNotFound() throws Exception {
        when(trackService.recordTrackPlay(5L))
                .thenThrow(new ResourceNotFoundException("Track with id 5 not found"));

        mockMvc.perform(post("/tracks/5/play")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Track with id 5 not found"));
    }

    @Test
    void playTrack_whenCooldownIsActive_returnsTooManyRequests() throws Exception {
        when(trackService.recordTrackPlay(5L))
                .thenThrow(new CooldownActiveException("Please wait 10 seconds before recording another play."));

        mockMvc.perform(post("/tracks/5/play")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Please wait 10 seconds before recording another play."));
    }

    @Test
    void playTrack_whenBodyIsNotEmpty_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tracks/5/play")
                .contentType("application/json")
                .content("{\"unexpected\":\"value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body must be empty."));

        verifyNoInteractions(trackService);
    }

    @Test
    void completeTrackListen_whenRequestIsValid_returnsOk() throws Exception {
        when(trackService.recordTrackCompletion(5L)).thenReturn(new MessageResponse("Full listen recorded"));

        mockMvc.perform(post("/tracks/5/complete")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Full listen recorded"));

        verify(trackService).recordTrackCompletion(5L);
    }

    @Test
    void completeTrackListen_whenTrackIdIsInvalidType_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tracks/not-a-number/complete")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(trackService);
    }

    @Test
    void completeTrackListen_whenTrackDoesNotExist_returnsNotFound() throws Exception {
        when(trackService.recordTrackCompletion(5L))
                .thenThrow(new ResourceNotFoundException("Track with id 5 not found"));

        mockMvc.perform(post("/tracks/5/complete")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Track with id 5 not found"));
    }

    @Test
    void completeTrackListen_whenBodyIsNotEmpty_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tracks/5/complete")
                .contentType("application/json")
                .content("{\"unexpected\":\"value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body must be empty."));

        verifyNoInteractions(trackService);
    }
}
