package software.decibel.services.track;

import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.decibel.dtos.track.*;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.FileType;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.TrackAlreadyPublishedException;
import software.decibel.exceptions.custom.UnauthorizedActionException;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;
import software.decibel.services.TagService;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.user.UserService;
import software.decibel.utils.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class TrackService {

    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    // Injected newly separated services
    private final LikeService likeService;
    private final RepostService repostService;

    private final FileUtilityAzure fileUtilityAzure;
    private final WaveFormUtility waveFormUtility;
    private final AudioUtility audioUtility;
    private final TrackMapper trackMapper;

    private final ObjectMapper objectMapper;
    private final TagService tagService;
    private final SimpMessagingTemplate messagingTemplate;

    public TrackStatusResponse getTrackStatus(Long trackId) {
        return trackMapper.toTrackStatusResponse(getTrackIfExistsById(trackId));
    }

    @Transactional
    public void deleteTrack(Long trackId) {
        Track track = getTrackIfExistsById(trackId);

        deleteTrackCover(trackId);
        deleteTrackAudio(trackId);
        deleteTrackWaveformData(trackId);

        trackRepository.delete(track);
    }

    public TrackUploadResponse uploadTrack(TrackUploadRequest request) {

        Long userId = JwtService.getCurrentUserId();
        User uploader = userService.getUserIfExistsById(userId);

        Track track = trackMapper.toEntity(request, uploader);

        List<String> tags = TagUtility.parseTags(request.tags());
        if (request.tags() != null) {
            addTrackTags(track, tags);
        }

        Track createdTrack = createUploadingTrack(track);

        try {
            byte[] audioBytes = request.audioFile().getBytes();
            String audioOriginalFilename = request.audioFile().getOriginalFilename();
            byte[] coverBytes = null;
            String coverOriginalFilename = null;

            if (request.coverImage() != null && !request.coverImage().isEmpty()) {
                coverBytes = request.coverImage().getBytes();
                coverOriginalFilename = request.coverImage().getOriginalFilename();
            }

            processTrackUploadAsync(createdTrack, request, audioBytes, audioOriginalFilename, coverBytes, coverOriginalFilename, userId);

            return trackMapper.toTrackUploadResponse(createdTrack);
        } catch (IOException e) {
            updateTrackState(createdTrack, TrackState.FAILED, null, null, "Failed to read upload data");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading uploaded files", e);
        }
    }

    @Async
    public void processTrackUploadAsync(Track createdTrack, TrackUploadRequest request, byte[] audioBytes,
            String audioOriginalFilename, byte[] coverBytes, String coverOriginalFilename, Long userId) {
        try {
            int audioWeight = 50;
            int coverWeight = 15;
            int waveformWeight = 15;
            int durationWeight = 15;
            int finalWeight = 5;

            updateTrackState(createdTrack, TrackState.UPLOADING, 0, "Processing started", null);

            String trackUrl = fileUtilityAzure.saveFileFromStream(
                    new ByteArrayInputStream(audioBytes),
                    (long) audioBytes.length,
                    FileType.AUDIO,
                    audioOriginalFilename,
                    createProgressCallback(createdTrack, 0, audioWeight, "Uploading audio file"));

            String coverUrl = null;
            if (coverBytes != null) {
                coverUrl = fileUtilityAzure.saveFileFromStream(
                        new ByteArrayInputStream(coverBytes),
                        (long) coverBytes.length,
                        FileType.TRACK_COVERS,
                        coverOriginalFilename,
                        createProgressCallback(createdTrack, audioWeight, coverWeight, "Uploading cover image"));
            } else {
                updateTrackState(createdTrack, TrackState.UPLOADING, audioWeight + coverWeight, "No cover provided", null);
            }

            updateTrackState(createdTrack, TrackState.UPLOADING, audioWeight + coverWeight, "Generating waveform", null);
            List<Float> waveformData = objectMapper.readValue(request.waveformData(), new TypeReference<List<Float>>() {
            });
            String waveformUrl = waveFormUtility.saveWaveformToAzure(waveformData, request.title());
            updateTrackState(createdTrack, TrackState.UPLOADING, audioWeight + coverWeight + waveformWeight, "Waveform ready", null);

            createdTrack.setTrackUrl(trackUrl);
            createdTrack.setCoverUrl(coverUrl);
            createdTrack.setWaveformUrl(waveformUrl);

            updateTrackState(createdTrack, TrackState.PROCESSING, audioWeight + coverWeight + waveformWeight, "Extracting audio duration", null);
            createdTrack.setDurationSeconds(
                    audioUtility.getAudioFileDurationInSeconds(audioBytes, audioOriginalFilename, request.title()));
            updateTrackState(createdTrack, TrackState.PROCESSING, audioWeight + coverWeight + waveformWeight + durationWeight, "Duration extracted", null);

            createdTrack.setState(TrackState.FINISHED);
            updateTrackState(createdTrack, TrackState.FINISHED, 100, "Done", null);

            trackRepository.save(createdTrack);
            User user = userService.getUserIfExistsById(userId);
            user.setTrackCount(user.getTrackCount() + 1);
            userRepository.save(user);

        } catch (Exception e) {
            String errorMessage = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "An unexpected error occurred during track processing";
            updateTrackState(createdTrack, TrackState.FAILED, null, null, errorMessage);
        }
    }

    private ProgressCallback createProgressCallback(Track track, int startPercentage, int weight, String stepName) {
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
                    updateTrackState(track, TrackState.UPLOADING, totalProgress, stepName, null);
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

    @Transactional
    public Track createUploadingTrack(Track track) {
        track.setState(TrackState.UPLOADING);
        Track saved = trackRepository.save(track);
        messagingTemplate.convertAndSend(
                "/topic/track-status/" + saved.getId(),
                new TrackStatusResponse(TrackState.UPLOADING, saved.getId()));
        return saved;
    }

    @Transactional
    public void updateTrackState(Track t, TrackState state) {
        updateTrackState(t, state, null, null, null);
    }

    @Transactional
    public void updateTrackState(Track t, TrackState state, Integer progress, String stepName, String errorMessage) {
        t.setState(state);
        trackRepository.save(t);
        messagingTemplate.convertAndSend(
                "/topic/track-status/" + t.getId(),
                new TrackStatusResponse(state, t.getId(), progress, stepName, errorMessage));
    }

    public Track getTrackIfExistsById(Long trackId) {
        return trackRepository
                .findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
    }

    @Transactional
    public void deleteTrackCover(Long trackId) {
        Track track = getTrackIfExistsById(trackId);
        if (track.getCoverUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(track.getCoverUrl());
            track.setCoverUrl(null);
            trackRepository.save(track);
        }
    }

    @Transactional
    public void deleteTrackAudio(Long trackId) {
        Track track = getTrackIfExistsById(trackId);
        if (track.getTrackUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(track.getTrackUrl());
            track.setTrackUrl(null);
            trackRepository.save(track);
        }
    }

    @Transactional
    public void deleteTrackWaveformData(Long trackId) {
        Track track = getTrackIfExistsById(trackId);
        if (track.getWaveformUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(track.getWaveformUrl());
            track.setWaveformUrl(null);
            trackRepository.save(track);
        }
    }

    @Transactional
    public void addTrackTags(Track track, List<String> tagTitles) {
        List<Tag> tags = tagTitles.stream().map(tagService::getOrCreateTag).collect(Collectors.toList());
        track.setTags(tags);
        trackRepository.save(track);
    }

    @Transactional
    public TrackPatchResponse updateTrack(Long trackId, TrackPatchRequest request) {
        Track track = getTrackIfExistsById(trackId);

        if (request.title() != null) {
            track.setTitle(request.title());
        }
        if (request.genre() != null) {
            track.setGenre(request.genre());
        }
        if (request.description() != null) {
            track.setDescription(request.description());
        }
        if (request.releaseDate() != null) {
            track.setReleaseDate(request.releaseDate());
        }
        if (request.isPrivate() != null) {
            track.setVisibility(request.isPrivate() ? Visibility.PRIVATE : Visibility.PUBLIC);
        }

        if (request.coverImage() != null && !request.coverImage().isEmpty()) {
            deleteTrackCover(trackId);
            String newCoverUrl = fileUtilityAzure.saveFile(request.coverImage(), FileType.TRACK_COVERS);
            track.setCoverUrl(newCoverUrl);
        }

        List<String> tags = TagUtility.parseTags(request.tags());
        if (request.tags() != null) {
            addTrackTags(track, tags);
        }

        return trackMapper.toTrackPatchResponse(trackRepository.save(track));
    }

    public TrackWaveFormUrlResponse getTrackWaveformUrl(Long trackId) {
        Track track = getTrackIfExistsById(trackId);
        return trackMapper.toTrackWaveFormUrlResponse(track);
    }

    public TrackPageResponse getCurrentUserTracks(int page, int size) {
        Long userId = JwtService.getCurrentUserId();
        return getAllTracksByUserId(userId, page, size);
    }

    private TrackPageResponse getAllTracksByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Track> result = trackRepository.findByUploaderId(userId, pageable);

        // Fetch ID 
        Set<Long> likedTrackIds = likeService.getLikedTrackIds(userId);
        Set<Long> repostedTrackIds = repostService.getRepostedTrackIds(userId);

        return trackMapper.toPageResponse(result, likedTrackIds, repostedTrackIds);
    }

    public TrackPageResponse getPublicTracksByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Track> result = trackRepository.findByUploaderIdAndVisibility(userId, Visibility.PUBLIC, pageable);

        // Fetch ID
        Set<Long> likedTrackIds = likeService.getLikedTrackIds(userId);
        Set<Long> repostedTrackIds = repostService.getRepostedTrackIds(userId);

        return trackMapper.toPageResponse(result, likedTrackIds, repostedTrackIds);
    }

    @Transactional
    public TrackPublishResponse publishTrack(Long trackId) {
        Track track = getTrackIfExistsById(trackId);

        if (!track.getUploader().getId().equals(JwtService.getCurrentUserId())) {
            throw new UnauthorizedActionException("You are not allowed to publish this track.");
        }

        if (track.isPublished()) {
            throw new TrackAlreadyPublishedException(trackId);
        }

        String slug = SlugUtility.generateUniqueSlug(track.getTitle(), trackRepository::existsBySlug);

        track.setSlug(slug);
        track.setPublished(true);
        track.setPublishedAt(LocalDateTime.now());

        return trackMapper.toTrackPublishResponse(trackRepository.save(track));
    }

    public TrackResponse getTrackData(Long trackId) {
        Track track = getTrackIfExistsById(trackId);
        Long currentUserId = null;
        try {
            // Attempt to get the current user ID. 
            currentUserId = JwtService.getCurrentUserId();
        } catch (Exception e) {
            // User is not logged in, leave currentUserId as null
        }
        //privacy check
        if (track.getVisibility() == Visibility.PRIVATE) {
            // If the user isn't logged in, or isn't the owner, hide the track's existence
            if (currentUserId == null || !track.getUploader().getId().equals(currentUserId)) {
                throw new ResourceNotFoundException("Track with id " + trackId + " not found");
            }
        }
        return buildTrackResponse(track, currentUserId);
    }

    public TrackResponse getCurrentUserTrackData(Long trackId) {
        Track track = getTrackIfExistsById(trackId);
        Long currentUserId = JwtService.getCurrentUserId();

        // Check if the track actually belongs to the current user
        if (!track.getUploader().getId().equals(currentUserId)) {
            throw new UnauthorizedActionException("You do not have permission to access this track.");
        }

        return buildTrackResponse(track, currentUserId);
    }

    //HELPER functions for track response
    private TrackResponse buildTrackResponse(Track track, Long userId) {
        boolean isLiked = false;
        boolean isReposted = false;

        if (userId != null) {
            isLiked = likeService.getLikedTrackIds(userId).contains(track.getId());
            isReposted = repostService.getRepostedTrackIds(userId).contains(track.getId());
        }

        return trackMapper.toTrackResponseSingle(track, isLiked, isReposted);
    }
}
