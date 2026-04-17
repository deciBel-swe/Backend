package software.decibel.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import software.decibel.dtos.track.TrackStatusResponse;
import software.decibel.entities.Track;
import software.decibel.enums.TrackState;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.track.TrackAsyncProcessor;

@ExtendWith(MockitoExtension.class)
class TrackAsyncProcessorTest {

    // 1. Mock the dependencies that the Processor needs
    @Mock
    private TrackRepository trackRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    // 2. Inject the mocks into the REAL Processor
    @InjectMocks
    private TrackAsyncProcessor trackAsyncProcessor;

    // Helper method: MUST return a real object, not a mock
    private Track createTrack(Long id) {
        Track track = new Track();
        track.setId(id);
        return track;
    }

    @Test
    void shouldUpdateTrackStateAndBroadcast() {
        // Arrange
        Long trackId = 1L;
        String uploadId = "test-uuid-1234";
        Track track = createTrack(trackId);

        when(trackRepository.findById(eq(trackId))).thenReturn(Optional.of(track));

        // Act
        trackAsyncProcessor.updateDbAndBroadcast(trackId, uploadId, TrackState.PROCESSING, null, null, null, null);

        // Assert
        assertEquals(TrackState.PROCESSING, track.getState());
        verify(trackRepository).save(track);
        verify(messagingTemplate)
                .convertAndSend(
                        eq("/topic/track-status/" + uploadId),
                        argThat((TrackStatusResponse response)
                                -> response.trackState() == TrackState.PROCESSING
                        && response.trackId().equals(trackId)
                        && response.progressPercentage() == null
                        )
                );
    }

    @Test
    void shouldUpdateTrackStateWithRichStatus() {
        // Arrange
        Long trackId = 1L;
        String uploadId = "test-uuid-1234";
        Track track = createTrack(trackId);

        when(trackRepository.findById(eq(trackId))).thenReturn(Optional.of(track));

        // Act
        trackAsyncProcessor.updateDbAndBroadcast(trackId, uploadId, TrackState.UPLOADING, 50, "Step", "Error", null);

        // Assert
        assertEquals(TrackState.UPLOADING, track.getState());
        verify(trackRepository).save(track);
        verify(messagingTemplate)
                .convertAndSend(
                        eq("/topic/track-status/" + uploadId),
                        argThat((TrackStatusResponse response)
                                -> response.trackState() == TrackState.UPLOADING
                        && response.trackId().equals(trackId)
                        && response.progressPercentage().equals(50)
                        && "Step".equals(response.stepName())
                        && "Error".equals(response.errorMessage())
                        )
                );
    }
}
