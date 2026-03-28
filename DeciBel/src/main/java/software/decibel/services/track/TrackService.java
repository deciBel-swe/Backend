package software.decibel.services.track;

import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
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
import software.decibel.entities.Like;
import software.decibel.entities.Repost;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.FileType;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.TrackAlreadyPublishedException;
import software.decibel.exceptions.custom.UnauthorizedActionException;
import software.decibel.mappers.LikeMapper;
import software.decibel.mappers.RepostMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.LikeRepository;
import software.decibel.repositories.RepostRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.JwtService;
import software.decibel.services.TagService;
import software.decibel.services.user.UserService;
import software.decibel.utils.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class TrackService {

    private final TrackRepository trackRepository;
    private final LikeRepository likeRepository;
    private final RepostRepository repostRepository;
    private final UserService userService;

    private final FileUtilityAzure fileUtilityAzure;
    private final WaveFormUtility waveFormUtility;
    private final AudioUtility audioUtility;
    private final TrackMapper trackMapper;
    private final LikeMapper likeMapper;
    private final RepostMapper repostMapper;

    private final ObjectMapper objectMapper;

    private final TagService tagService;

    // Returns track's status
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

    private final SimpMessagingTemplate messagingTemplate;

    // Takes track upload request and saves track
    // Not transactional as track's insertions an updates must survive to reflect
    // track states
    public TrackUploadResponse uploadTrack(TrackUploadRequest request) {

        // get userid and user from jwt
        Long userId = JwtService.getCurrentUserId();
        User uploader = userService.getUserIfExistsById(userId);

        // convert track to entity and save as UPLOADING
        Track track = trackMapper.toEntity(request, uploader);

        // Parse json tag string to a list of tag strings
        List<String> tags = TagUtility.parseTags(request.tags());
        if (request.tags() != null) {
            addTrackTags(track, tags);
        }

        Track createdTrack = createUploadingTrack(track);

        // Capture files' data and metadata for async processing
        try {
            byte[] audioBytes = request.audioFile().getBytes();
            String audioOriginalFilename = request.audioFile().getOriginalFilename();
            byte[] coverBytes = null;
            String coverOriginalFilename = null;
            if (request.coverImage() != null && !request.coverImage().isEmpty()) {
                coverBytes = request.coverImage().getBytes();
                coverOriginalFilename = request.coverImage().getOriginalFilename();
            }

            // Start processing in the background
            processTrackUploadAsync(createdTrack, request, audioBytes, audioOriginalFilename, coverBytes,
                    coverOriginalFilename);

            return trackMapper.toTrackUploadResponse(createdTrack);
        } catch (IOException e) {
            updateTrackState(createdTrack, TrackState.FAILED, null, null, "Failed to read upload data");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading uploaded files", e);
        }
    }

    @Async
    public void processTrackUploadAsync(Track createdTrack, TrackUploadRequest request, byte[] audioBytes,
            String audioOriginalFilename, byte[] coverBytes, String coverOriginalFilename) {
        try {
            // Weights for each step to make progress feel natural (total = 100)
            int audioWeight = 50;
            int coverWeight = 15;
            int waveformWeight = 15;
            int durationWeight = 15;
            int finalWeight = 5;

            // Start with explicit 0% update
            updateTrackState(createdTrack, TrackState.UPLOADING, 0, "Processing started", null);

            // 1. Save audio file (0% - 50%)
            String trackUrl = fileUtilityAzure.saveFileFromStream(
                    new ByteArrayInputStream(audioBytes),
                    (long) audioBytes.length,
                    FileType.AUDIO,
                    audioOriginalFilename,
                    createProgressCallback(createdTrack, 0, audioWeight, "Uploading audio file"));

            // 2. Save cover image (50% - 65%)
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

            // 3. Generating waveform (65% - 80%)
            updateTrackState(createdTrack, TrackState.UPLOADING, audioWeight + coverWeight, "Generating waveform", null);
            List<Float> waveformData = objectMapper.readValue(request.waveformData(), new TypeReference<List<Float>>() {
            });
            String waveformUrl = waveFormUtility.saveWaveformToAzure(waveformData, request.title());
            updateTrackState(createdTrack, TrackState.UPLOADING, audioWeight + coverWeight + waveformWeight, "Waveform ready", null);

            // Set urls manually
            createdTrack.setTrackUrl(trackUrl);
            createdTrack.setCoverUrl(coverUrl);
            createdTrack.setWaveformUrl(waveformUrl);

            // 4. Extracting audio duration (80% - 95%)
            updateTrackState(createdTrack, TrackState.PROCESSING, audioWeight + coverWeight + waveformWeight, "Extracting audio duration", null);
            createdTrack.setDurationSeconds(
                    audioUtility.getAudioFileDurationInSeconds(audioBytes, audioOriginalFilename, request.title()));
            updateTrackState(createdTrack, TrackState.PROCESSING, audioWeight + coverWeight + waveformWeight + durationWeight, "Duration extracted", null);

            // 5. Done (100%)
            createdTrack.setState(TrackState.FINISHED);
            updateTrackState(createdTrack, TrackState.FINISHED, 100, "Done", null);

            trackRepository.save(createdTrack);

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
                
                // Always send 0%, lastReportedProgress, or multiple of 5 (to avoid flooding)
                boolean isStartOfStep = totalProgress == startPercentage;
                boolean isEndOfStep = totalProgress == startPercentage + weight;
                boolean isMultipleOfFive = totalProgress % 5 == 0;

                if (totalProgress != lastReportedProgress && (isStartOfStep || isEndOfStep || isMultipleOfFive)) {
                    updateTrackState(track, TrackState.UPLOADING, totalProgress, stepName, null);
                    lastReportedProgress = totalProgress;

                    // Small artificial delay (20ms) only during "Uploading" steps to make it visible
                    // if the file is small and in-memory.
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

    // ------------- TRACK SERVICE HELPER FUNCTIONS ---------------------
    // Function to save track entity & set state = uploading
    @Transactional
    public Track createUploadingTrack(Track track) {
        track.setState(TrackState.UPLOADING);
        Track saved = trackRepository.save(track);
        messagingTemplate.convertAndSend(
                "/topic/track-status/" + saved.getId(),
                new TrackStatusResponse(TrackState.UPLOADING, saved.getId()));
        return saved;
    }

    // Function to update track entity's state and save
    @Transactional
    public void updateTrackState(Track t, TrackState state) {
        updateTrackState(t, state, null, null, null);
    }

    // Function to update track entity's state and save with rich status
    @Transactional
    public void updateTrackState(Track t, TrackState state, Integer progress, String stepName, String errorMessage) {
        t.setState(state);
        trackRepository.save(t);
        messagingTemplate.convertAndSend(
                "/topic/track-status/" + t.getId(),
                new TrackStatusResponse(state, t.getId(), progress, stepName, errorMessage));
    }

    // Returns track entity by id and throws exception if not found
    public Track getTrackIfExistsById(Long trackId) {
        return trackRepository
                .findById(trackId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
    }

    // Deletes track cover from azure & sets coverUrl = null
    @Transactional
    public void deleteTrackCover(Long trackId) {
        Track track = getTrackIfExistsById(trackId);

        if (track.getCoverUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(track.getCoverUrl());
            track.setCoverUrl(null);
            trackRepository.save(track);
        }
    }

    // Deletes track audio from azure & sets trackUrl = null
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

    // Adds tags to tracks (whether tags already exist or create ones) - tags will
    // be title case
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

        // Parse json tag string to a list of tag strings
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

    // Gets tracks by user id (and is pageable)
    private TrackPageResponse getAllTracksByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Track> result = trackRepository.findByUploaderId(userId, pageable);

        return trackMapper.toPageResponse(result);
    }

    // Gets tracks by user id (and is pageable) - only public tracks
    public TrackPageResponse getPublicTracksByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Track> result = trackRepository.findByUploaderIdAndVisibility(userId, Visibility.PUBLIC, pageable);

        return trackMapper.toPageResponse(result);
    }

    @Transactional
    public RepostResponse repostTrack(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        if (repostRepository.existsByUserAndTrack(user, track)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Track already reposted");
        }

        repostRepository.save(Repost.builder()
                .user(user)
                .track(track)
                .build());

        track.setRepostCount(track.getRepostCount() + 1);
        trackRepository.save(track);

        return repostMapper.toRepostResponse(true);
    }

    @Transactional
    public RepostResponse removeRepost(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        Repost repost = repostRepository.findByUserAndTrack(user, track)
                .orElseThrow(() -> new ResourceNotFoundException("Repost not found for track with id " + trackId));

        repostRepository.delete(repost);

        if (track.getRepostCount() > 0) {
            track.setRepostCount(track.getRepostCount() - 1);
            trackRepository.save(track);
        }

        return repostMapper.toRepostResponse(false);
    }

    @Transactional
    public LikeResponse likeTrack(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        if (likeRepository.existsByUserAndTrack(user, track)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Track already liked");
        }

        likeRepository.save(Like.builder()
                .user(user)
                .track(track)
                .build());

        track.setLikeCount(track.getLikeCount() + 1);
        trackRepository.save(track);

        return likeMapper.toLikeResponse(true);
    }

    @Transactional
    public LikeResponse unlikeTrack(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        Like like = likeRepository.findByUserAndTrack(user, track)
                .orElseThrow(() -> new ResourceNotFoundException("Like not found for track with id " + trackId));

        likeRepository.delete(like);

        if (track.getLikeCount() > 0) {
            track.setLikeCount(track.getLikeCount() - 1);
            trackRepository.save(track);
        }

        return likeMapper.toLikeResponse(false);
    }

  // publish track
  @Transactional
  public TrackPublishResponse publishTrack(Long trackId) {
    Track track = getTrackIfExistsById(trackId);

    // Check user trying to publish track is the uploader
    if (!track.getUploader().getId().equals(JwtService.getCurrentUserId())) {
      throw new UnauthorizedActionException("You are not allowed to publish this track.");
    }

    // if track alr published
    if (track.isPublished()) {
      throw new TrackAlreadyPublishedException(trackId);
    }

    // pass the func that checks if slug is unique or not
    String slug = SlugUtility.generateUniqueSlug(track.getTitle(), trackRepository::existsBySlug);

    track.setSlug(slug);
    track.setPublished(true);
    track.setPublishedAt(LocalDateTime.now());

    return trackMapper.toTrackPublishResponse(trackRepository.save(track));
  }
}
