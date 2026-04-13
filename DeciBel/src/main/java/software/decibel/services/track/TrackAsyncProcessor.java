package software.decibel.services.track;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.track.TrackResponse;
import software.decibel.dtos.track.TrackStatusResponse;
import software.decibel.dtos.track.TrackUploadRequest;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.FileType;
import software.decibel.enums.TrackState;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.user.UserService;
import software.decibel.utils.AudioUtility;
import software.decibel.utils.FileUtilityAzure;
import software.decibel.utils.ProgressCallback;
import software.decibel.utils.WaveFormUtility;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackAsyncProcessor {

    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TrackMapper trackMapper;
    private final FileUtilityAzure fileUtilityAzure;
    private final WaveFormUtility waveFormUtility;
    private final AudioUtility audioUtility;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Async
    public void processTrackUploadAsync(Long trackId, String uploadId, TrackUploadRequest request, byte[] audioBytes,
            String audioOriginalFilename, byte[] coverBytes, String coverOriginalFilename, Long userId) {
        try {
            int audioWeight = 50;
            int coverWeight = 15;
            int waveformWeight = 15;
            int durationWeight = 15;

            updateDbAndBroadcast(trackId, uploadId, TrackState.UPLOADING, 0, "Processing started", null, null);

            String trackUrl = fileUtilityAzure.saveFileFromStream(
                    new ByteArrayInputStream(audioBytes),
                    (long) audioBytes.length,
                    FileType.AUDIO,
                    audioOriginalFilename,
                    createProgressCallback(trackId, uploadId, 0, audioWeight, "Uploading audio file"));

            String coverUrl = null;
            if (coverBytes != null) {
                coverUrl = fileUtilityAzure.saveFileFromStream(
                        new ByteArrayInputStream(coverBytes),
                        (long) coverBytes.length,
                        FileType.TRACK_COVERS,
                        coverOriginalFilename,
                        createProgressCallback(trackId, uploadId, audioWeight, coverWeight, "Uploading cover image"));
            } else {
                broadcastProgress(trackId, uploadId, TrackState.UPLOADING, audioWeight + coverWeight, "No cover provided");
            }

            broadcastProgress(trackId, uploadId, TrackState.UPLOADING, audioWeight + coverWeight, "Uploading waveform data");
            List<Float> waveformData = objectMapper.readValue(request.waveformData(), new TypeReference<List<Float>>() {
            });
            String waveformUrl = waveFormUtility.saveWaveformToAzure(waveformData, request.title());
            broadcastProgress(trackId, uploadId, TrackState.UPLOADING, audioWeight + coverWeight + waveformWeight, "Waveform data uploaded");

            broadcastProgress(trackId, uploadId, TrackState.PROCESSING, audioWeight + coverWeight + waveformWeight, "Extracting audio duration");
            int durationSeconds = audioUtility.getAudioFileDurationInSeconds(audioBytes, audioOriginalFilename, request.title());
            broadcastProgress(trackId, uploadId, TrackState.PROCESSING, audioWeight + coverWeight + waveformWeight + durationWeight, "Duration extracted");
            //needed becuase lamda fucntions require effectively final variables, and the trackUrl and coverUrl are needed in the transaction below
            final String finalCoverUrl = coverUrl;
            transactionTemplate.execute(status -> {
                Track track = trackRepository.findById(trackId).orElseThrow();
                track.setTrackUrl(trackUrl);
                track.setCoverUrl(finalCoverUrl);
                track.setWaveformUrl(waveformUrl);
                track.setDurationSeconds(durationSeconds);
                track.setState(TrackState.FINISHED);
                track.getTags().size();
                track.getUploader().getUsername();

                trackRepository.save(track);

                User user = userService.getUserIfExistsById(userId);
                user.setTrackCount(user.getTrackCount() + 1);
                userRepository.save(user);

                // Now the mapper will work perfectly because the tags and uploader are fully loaded
                TrackResponse finalResponse = trackMapper.toTrackResponseSingle(track, false, false);

                // Broadcast the final success message
                messagingTemplate.convertAndSend(
                        "/topic/track-status/" + uploadId,
                        new TrackStatusResponse(TrackState.FINISHED, trackId, 100, "Done", null, finalResponse));

                return null;
            });

        } catch (Exception e) {
            String errorMessage = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "An unexpected error occurred during track processing";
            log.error("Error during async upload for trackId: {}", trackId, e);
            updateDbAndBroadcast(trackId, uploadId, TrackState.FAILED, null, null, errorMessage, null);
        }
    }

    private ProgressCallback createProgressCallback(Long trackId, String uploadId, int startPercentage, int weight, String stepName) {
        return new ProgressCallback() {
            private int lastReportedProgress = -1;

            @Override
            public void onProgress(long bytesRead, long totalBytes) {
                int subProgress = (int) ((bytesRead * weight) / totalBytes);
                int totalProgress = startPercentage + subProgress;

                boolean isStartOfStep = totalProgress == startPercentage;
                boolean isEndOfStep = totalProgress == startPercentage + weight;
                boolean isMultipleOfFive = totalProgress % 5 == 0;

                if (totalProgress != lastReportedProgress && (isStartOfStep || isEndOfStep || isMultipleOfFive)) {
                    broadcastProgress(trackId, uploadId, TrackState.UPLOADING, totalProgress, stepName);
                    lastReportedProgress = totalProgress;

                    if (stepName.contains("Uploading")) {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        };
    }

    private void broadcastProgress(Long trackId, String uploadId, TrackState state, Integer progress, String stepName) {
        messagingTemplate.convertAndSend(
                "/topic/track-status/" + uploadId,
                new TrackStatusResponse(state, trackId, progress, stepName, null, null));
    }

    @Transactional
    public void updateDbAndBroadcast(Long trackId, String uploadId, TrackState state, Integer progress, String stepName, String errorMessage, TrackResponse finalResponse) {
        trackRepository.findById(trackId).ifPresent(t -> {
            t.setState(state);
            trackRepository.save(t);
        });
        messagingTemplate.convertAndSend(
                "/topic/track-status/" + uploadId,
                new TrackStatusResponse(state, trackId, progress, stepName, errorMessage, finalResponse));
    }
}
