package software.decibel.services.track;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.text.WordUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.Resource;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.track.requests.TrackPatchRequest;
import software.decibel.dtos.track.requests.TrackUploadRequest;
import software.decibel.dtos.track.responses.TrackPageResponse;
import software.decibel.dtos.track.responses.TrackPatchResponse;
import software.decibel.dtos.track.responses.TrackPublishResponse;
import software.decibel.dtos.track.responses.TrackResponse;
import software.decibel.dtos.track.responses.TrackStatusResponse;
import software.decibel.dtos.track.responses.TrackUploadResponse;
import software.decibel.dtos.track.responses.TrackWaveFormUrlResponse;
import software.decibel.entities.ListeningHistory;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.FileType;
import software.decibel.enums.ResourceType;
import software.decibel.enums.TrackAccess;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.CooldownActiveException;
import software.decibel.exceptions.custom.NoStationResultsException;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.TrackAlreadyPublishedException;
import software.decibel.exceptions.custom.UnauthorizedActionException;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.CommentRepository;
import software.decibel.repositories.ListeningHistoryRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.JwtService;
import software.decibel.services.TagService;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.user.UserService;
import software.decibel.utils.FileUtilityAzure;
import software.decibel.utils.SlugUtility;
import software.decibel.utils.TagUtility;
import software.decibel.utils.TrackChecksUtil;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackService {

    private final TrackRepository trackRepository;
    private final TrackLikeRepository likeRepository;
    private final TrackRepostRepository repostRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final ListeningHistoryRepository listeningHistoryRepository;

    private final TrackPlaybackService trackPlaybackService;

    private final LikeService likeService;
    private final RepostService repostService;
    private final TrackTokenService trackTokenService;

    private final FileUtilityAzure fileUtilityAzure;
    private final TrackMapper trackMapper;

    private final TagService tagService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TrackChecksUtil trackChecksUtil;

    private final PlaylistRepository playlistRepository;

    //Async Processor
    private final TrackAsyncProcessor trackAsyncProcessor;

    public TrackStatusResponse getTrackStatus(Long trackId) {
        return trackMapper.toTrackStatusResponse(trackChecksUtil.getTrackIfExistsById(trackId));
    }

    @Transactional
    public MessageResponse recordTrackPlay(Long trackId) {
        Track track = getTrackIfExistsById(trackId);
        Long currentUserId = JwtService.getCurrentUserId();

        if (currentUserId != null) {
            User user = userService.getUserIfExistsById(currentUserId);
            enforcePlayCooldown(currentUserId, track);
            listeningHistoryRepository.save(
                    ListeningHistory.builder()
                            .user(user)
                            .track(track)
                            .completed(false)
                            .build());
        }
        /*
        Assumed that Guest users can also play tracks, but we won't record their plays in listening history 
        (since we have no user to associate it with) 
        and we won't enforce cooldowns on them. 
        We will still increment the play count for the track though.
         */
        track.setPlayCount(track.getPlayCount() + 1);
        trackRepository.save(track);

        return new MessageResponse("Play recorded");
    }

    @Transactional
    public MessageResponse recordTrackCompletion(Long trackId) {
        Long currentUserId = JwtService.getCurrentUserId();
        userService.getUserIfExistsById(currentUserId);

        Track track = getTrackIfExistsById(trackId);
        listeningHistoryRepository.findTopByUserIdAndTrackIdAndCompletedFalseOrderByPlayedAtDesc(currentUserId, trackId)
                .ifPresent(history -> {
                    history.setCompleted(true);
                    listeningHistoryRepository.save(history);
                    if (track.getCompletedPlayCount() < track.getPlayCount()) {
                        track.setCompletedPlayCount(track.getCompletedPlayCount() + 1);
                    }
                });

        if (track.getPlayCount() > 0) {
            track.setPlayThroughRate((double) track.getCompletedPlayCount() / track.getPlayCount());
        } else {
            track.setPlayThroughRate(0.0);
        }

        trackRepository.save(track);
        return new MessageResponse("Full listen recorded");
    }

    //delete track
    @Transactional
    public void deleteTrack(Long trackId) {
        Track track = trackChecksUtil.getTrackIfExistsById(trackId);

        User uploader = track.getUploader();
        Long currentUserId = JwtService.getCurrentUserId();
        if (!Objects.equals(currentUserId, uploader.getId())) {
            throw new UnauthorizedActionException("You cannot delete a track you didn't upload.");
        }

        //fetch track url data before deleting
        final String audioUrl = track.getTrackUrl();
        final String coverUrl = track.getCoverUrl();
        final String waveformUrl = track.getWaveformUrl();
        //delete from DB
        likeRepository.deleteAllByTrackId(trackId);
        repostRepository.deleteAllByTrackId(trackId);
        commentRepository.deleteAllByTrackId(trackId);
        playlistRepository.removeTrackFromAllPlaylists(trackId);

        // update track count
        uploader.setTrackCount(uploader.getTrackCount() - 1);

        // if free uploader deleted a nn-blocked track they free a slot
        if (track.getAccess() != TrackAccess.BLOCKED && uploader.getTier() == AccountTier.FREE) {
            uploader.setFreeTracksLeft(uploader.getFreeTracksLeft() + 1);
        }

        trackRepository.delete(track);
        //delete from azure
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (coverUrl != null) {
                        fileUtilityAzure.deleteFileByUrl(coverUrl);
                    }
                    if (audioUrl != null) {
                        fileUtilityAzure.deleteFileByUrl(audioUrl);
                    }
                    if (waveformUrl != null) {
                        fileUtilityAzure.deleteFileByUrl(waveformUrl);
                    }
                } catch (Exception e) {
                    log.error("Database deletion succeeded, but failed to delete files for track {}", trackId, e);
                }
            }
        });
    }

    /**
     * Admin-privileged track deletion. Fetches the track directly from the
     * repository, bypassing the ownership, block, and visibility checks in
     * getTrackIfExistsById() which rely on a UserPrincipal in the security
     * context (not present for admin requests).
     */
    @Transactional
    public void adminDeleteTrack(Long trackId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new software.decibel.exceptions.custom.ResourceNotFoundException(
                "Track with id " + trackId + " not found"));

        final String audioUrl = track.getTrackUrl();
        final String coverUrl = track.getCoverUrl();
        final String waveformUrl = track.getWaveformUrl();

        likeRepository.deleteAllByTrackId(trackId);
        repostRepository.deleteAllByTrackId(trackId);
        commentRepository.deleteAllByTrackId(trackId);
        playlistRepository.removeTrackFromAllPlaylists(trackId);
        trackRepository.delete(track);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (coverUrl != null) {
                        fileUtilityAzure.deleteFileByUrl(coverUrl);
                    }
                    if (audioUrl != null) {
                        fileUtilityAzure.deleteFileByUrl(audioUrl);
                    }
                    if (waveformUrl != null) {
                        fileUtilityAzure.deleteFileByUrl(waveformUrl);
                    }
                } catch (Exception e) {
                    log.error("Admin delete: DB succeeded but file deletion failed for track {}", trackId, e);
                }
            }
        });
    }

    public TrackResponse uploadTrack(TrackUploadRequest request, String uploadId) {
        Long userId = JwtService.getCurrentUserId();
        User uploader = userService.getUserIfExistsById(userId);

        // 1. Initialize DB record
        Track track = trackMapper.toEntity(request, uploader);
        TrackAccess finalAccess = trackPlaybackService.resolveUploadAccess(uploader, request.access());
        trackPlaybackService.updateFreeTracksLeft(uploader, null, finalAccess);
        track.setAccess(finalAccess);

        if (request.tags() != null) {
            addTrackTags(track, TagUtility.parseTags(request.tags()));
        }
        track.setGenre(WordUtils.capitalize(track.getGenre().trim().toLowerCase().replaceAll("\\s+", " ")));

        // Generate unique slug
        String slug = SlugUtility.generateUniqueSlug(track.getTitle(), s -> trackRepository.existsBySlug(s));
        track.setSlug(slug);

        Track createdTrack = createUploadingTrack(track, uploadId);

        try {
            // 2. Prepare files
            byte[] audioBytes = request.audioFile().getBytes();
            byte[] coverBytes = (request.coverImage() != null && !request.coverImage().isEmpty())
                    ? request.coverImage().getBytes() : null;

            // 3. Call Sync Processor and return final DTO
            return trackAsyncProcessor.processTrackUploadSync(
                    createdTrack.getId(),
                    uploadId,
                    request,
                    audioBytes,
                    request.audioFile().getOriginalFilename(),
                    coverBytes,
                    (request.coverImage() != null) ? request.coverImage().getOriginalFilename() : null,
                    userId);

        } catch (IOException e) {
            trackAsyncProcessor.updateDbAndBroadcast(createdTrack.getId(), uploadId, TrackState.FAILED, null, null, "Failed to read upload data", null);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading uploaded files", e);
        }
    }

    //upload track and subscribe via ID sent by the front/cross
    public TrackUploadResponse uploadTrackAsync(TrackUploadRequest request, String uploadId) {
        Track createdTrack = initializeTrackUpload(request, uploadId);
        Long userId = JwtService.getCurrentUserId();

        try {
            byte[] audioBytes = request.audioFile().getBytes();
            String audioOriginalFilename = request.audioFile().getOriginalFilename();
            byte[] coverBytes = null;
            String coverOriginalFilename = null;

            if (request.coverImage() != null && !request.coverImage().isEmpty()) {
                coverBytes = request.coverImage().getBytes();
                coverOriginalFilename = request.coverImage().getOriginalFilename();
            }

            // Hands off to async processor and returns immediately
            trackAsyncProcessor.processTrackUploadAsync(
                    createdTrack.getId(), uploadId, request, audioBytes, audioOriginalFilename, coverBytes, coverOriginalFilename, userId);

            return trackMapper.toTrackUploadResponse(createdTrack);

        } catch (IOException e) {
            trackAsyncProcessor.updateDbAndBroadcast(createdTrack.getId(), uploadId, TrackState.FAILED, null, null, "Failed to read upload data", null);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading uploaded files", e);
        }
    }

    @Transactional
    public Track createUploadingTrack(Track track, String uploadId) {
        track.setState(TrackState.UPLOADING);
        Track saved = trackRepository.save(track);
        messagingTemplate.convertAndSend(
                "/topic/track-status/" + uploadId,
                new TrackStatusResponse(TrackState.UPLOADING, saved.getId(), 0, "Initializing", null, null));
        return saved;
    }

    @Transactional
    public void deleteTrackCover(Long trackId) {
        Track track = trackChecksUtil.getTrackIfExistsById(trackId);

        User uploader = track.getUploader();
        Long currentUserId = JwtService.getCurrentUserId();
        if (!Objects.equals(currentUserId, uploader.getId())) {
            throw new UnauthorizedActionException(
                    "You cannot delete the cover for track you didn't upload.");
        }
        if (track.getCoverUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(track.getCoverUrl());
            track.setCoverUrl(null);
            trackRepository.save(track);
        }
    }

    @Transactional
    public void deleteTrackAudio(Long trackId) {
        Track track = trackChecksUtil.getTrackIfExistsById(trackId);
        if (track.getTrackUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(track.getTrackUrl());
            track.setTrackUrl(null);
            trackRepository.save(track);
        }
    }

    @Transactional
    public void deleteTrackWaveformData(Long trackId) {
        Track track = trackChecksUtil.getTrackIfExistsById(trackId);
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

        Track track = trackChecksUtil.getTrackIfExistsById(trackId);
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);

        // only uploader can patch
        if (!Objects.equals(userId, track.getUploader().getId())) {
            throw new UnauthorizedActionException("You cannot update a track you didn't upload.");
        }
        if (request.title() != null) {
            track.setTitle(request.title());
        }
        if (request.genre() != null) {
            track.setGenre(request.genre());
            WordUtils.capitalize(track.getGenre().trim().toLowerCase().replaceAll("\\s+", " "));
        }
        if (request.description() != null) {
            track.setDescription(request.description());
        }
        if (request.releaseDate() != null) {
            track.setReleaseDate(request.releaseDate());
        }
        if (request.isPrivate() != null) {
            Visibility newVisibility = request.isPrivate() ? Visibility.PRIVATE : Visibility.PUBLIC;

            // If transitioning from PUBLIC to PRIVATE, issue a new secret token
            if (track.getVisibility() == Visibility.PUBLIC && newVisibility == Visibility.PRIVATE) {
                trackTokenService.regenerateToken(track.getId());
            }

            track.setVisibility(newVisibility);
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

        if (request.access() != null) {
            TrackAccess finalAccess
                    = trackPlaybackService.resolvePatchAccess(user, track.getAccess(), request.access());

            // update free tracks left based on initial access and final access
            trackPlaybackService.updateFreeTracksLeft(user, track.getAccess(), finalAccess);
            track.setAccess(finalAccess);
        }

        return trackMapper.toTrackPatchResponse(trackRepository.save(track));
    }

    public TrackWaveFormUrlResponse getTrackWaveformUrl(Long trackId) {
        Track track = trackChecksUtil.getTrackIfExistsById(trackId);
        return trackMapper.toTrackWaveFormUrlResponse(track);
    }

    public TrackPageResponse getCurrentUserTracks(int page, int size) {
        Long userId = JwtService.getCurrentUserId();
        return getAllTracksByUserId(userId, page, size);
    }

    private TrackPageResponse getAllTracksByUserId(Long userId, int page, int size) {
        User user = userService.getUserIfExistsById(userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<Track> result = trackRepository.findByUploaderId(userId, pageable);

        Set<Long> likedTrackIds = likeService.getLikedTrackIds(userId);
        Set<Long> repostedTrackIds = repostService.getRepostedTrackIds(userId);

        return trackMapper.toPageResponse(result, user.getTier(), likedTrackIds, repostedTrackIds);
    }

    public TrackPageResponse getPublicTracksByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Track> result = trackRepository.findByUploaderIdAndVisibility(userId, Visibility.PUBLIC, pageable);

        Long currentUserId = null;
        try {
            currentUserId = JwtService.getCurrentUserId();
        } catch (Exception e) {
        }

        Set<Long> likedTrackIds = currentUserId != null ? likeService.getLikedTrackIds(currentUserId) : Set.of();
        Set<Long> repostedTrackIds = currentUserId != null ? repostService.getRepostedTrackIds(currentUserId) : Set.of();
        AccountTier currentTier = currentUserId != null ? userService.getUserIfExistsById(currentUserId).getTier() : AccountTier.FREE;

        return trackMapper.toPageResponse(result, currentTier, likedTrackIds, repostedTrackIds);
    }

    @Transactional
    public TrackPublishResponse publishTrack(Long trackId) {
        Track track = trackChecksUtil.getTrackIfExistsById(trackId);

        if (!track.getUploader().getId().equals(JwtService.getCurrentUserId())) {
            throw new UnauthorizedActionException(
                    "You are not allowed to publish a track you didn't upload.");
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

    public Resource resolveTrackSlug(String slug) {
        Long id = trackRepository.findTrackIdBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                "No track found with slug: " + slug));

        return new Resource(ResourceType.TRACK, id);
    }

    public TrackPageResponse getTrendingTracks(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Long currentUserId = null;
        try {
            currentUserId = JwtService.getCurrentUserId();
        } catch (Exception e) {
        }

        Page<Track> trendingTracks = trackRepository.findAllTrending(currentUserId, pageable);

        if (trendingTracks.isEmpty()) {
            throw new NoStationResultsException();
        }

        Set<Long> likedTrackIds = (currentUserId != null) ? likeService.getLikedTrackIds(currentUserId) : Set.of();
        Set<Long> repostedTrackIds = (currentUserId != null) ? repostService.getRepostedTrackIds(currentUserId) : Set.of();
        AccountTier currentTier = (currentUserId != null) ? userService.getUserIfExistsById(currentUserId).getTier() : AccountTier.FREE;

        return trackMapper.toPageResponse(trendingTracks, currentTier, likedTrackIds, repostedTrackIds);
    }

    public TrackResponse getTrackData(Long trackId) {
        Track track = trackChecksUtil.getTrackIfExistsById(trackId);
        Long currentUserId = null;
        try {
            currentUserId = JwtService.getCurrentUserId();
        } catch (Exception e) {
        }
        if (track.getVisibility() == Visibility.PRIVATE) {
            if (currentUserId == null || !track.getUploader().getId().equals(currentUserId)) {
                throw new ResourceNotFoundException("Track with id " + trackId + " not found");
            }
        }
        return buildTrackResponse(track, currentUserId);
    }

    public TrackResponse getCurrentUserTrackData(Long trackId) {
        Track track = trackChecksUtil.getTrackIfExistsById(trackId);
        Long currentUserId = JwtService.getCurrentUserId();

        if (!track.getUploader().getId().equals(currentUserId)) {
            throw new UnauthorizedActionException("You do not have permission to access this track.");
        }

        return buildTrackResponse(track, currentUserId);
    }

    //HELPER functions for track response
    private TrackResponse buildTrackResponse(Track track, Long userId) {
        boolean isLiked = false;
        boolean isReposted = false;
        AccountTier tier = AccountTier.FREE;

        if (userId != null) {
            isLiked = likeService.getLikedTrackIds(userId).contains(track.getId());
            isReposted = repostService.getRepostedTrackIds(userId).contains(track.getId());
            tier = userService.getUserIfExistsById(userId).getTier();
        }

        return trackMapper.toTrackResponseSingle(track, tier, isLiked, isReposted);
    }

    private Track initializeTrackUpload(TrackUploadRequest request, String uploadId) {
        Long userId = JwtService.getCurrentUserId();
        User uploader = userService.getUserIfExistsById(userId);

        Track track = trackMapper.toEntity(request, uploader);
        TrackAccess finalAccess = trackPlaybackService.resolveUploadAccess(uploader, request.access());
        trackPlaybackService.updateFreeTracksLeft(uploader, null, finalAccess);
        track.setAccess(finalAccess);

        List<String> tags = TagUtility.parseTags(request.tags());
        if (request.tags() != null) {
            addTrackTags(track, tags);
        }

        track.setGenre(WordUtils.capitalize(track.getGenre().trim().toLowerCase().replaceAll("\\s+", " ")));

        // Generate unique slug
        String slug = SlugUtility.generateUniqueSlug(track.getTitle(), s -> trackRepository.existsBySlug(s));
        track.setSlug(slug);

        return createUploadingTrack(track, uploadId);
    }

    private Track getTrackIfExistsById(Long trackId) {
        return trackChecksUtil.getTrackIfExistsById(trackId);
    }

    private void enforcePlayCooldown(Long userId, Track track) {
        listeningHistoryRepository.findTopByUserIdAndTrackIdOrderByPlayedAtDesc(userId, track.getId())
                .ifPresent(lastPlay -> {
                    int cooldownSeconds = Math.max(track.getDurationSeconds(), 0);
                    if (cooldownSeconds == 0 || lastPlay.getPlayedAt() == null) {
                        return;
                    }

                    LocalDateTime nextAllowedPlayAt = lastPlay.getPlayedAt().plusSeconds(cooldownSeconds);
                    if (nextAllowedPlayAt.isAfter(LocalDateTime.now())) {
                        long secondsLeft = Duration.between(LocalDateTime.now(), nextAllowedPlayAt).toSeconds();
                        throw new CooldownActiveException(
                                "Please wait " + Math.max(secondsLeft, 1) + " seconds before recording another play.");
                    }
                });
    }
}
