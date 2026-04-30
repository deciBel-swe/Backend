package software.decibel.services.playlist;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.Resource;
import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistTokenResponse;
import software.decibel.dtos.playlist.ReorderTracksRequest;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistRepost;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.FileType;
import software.decibel.enums.ResourceType;
import software.decibel.exceptions.custom.InvalidPlaylistOperationException;
import software.decibel.exceptions.custom.PlaylistAccessDeniedException;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.TrackAlreadyInPlaylistException;
import software.decibel.exceptions.custom.UnauthorizedActionException;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.PlaylistLikeRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistRepostRepository;
import software.decibel.repositories.PlaylistTokenRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserService;
import software.decibel.utils.FileUtilityAzure;
import software.decibel.utils.SlugUtility;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTokenRepository playlistTokenRepository;
    private final TrackRepository trackRepository;
    private final TrackLikeRepository trackLikeRepository;
    private final TrackRepostRepository trackRepostRepository;
    private final BlockRepository blockRepository;
    private final FileUtilityAzure fileUtilityAzure;
    private final PlaylistMapper playlistMapper;
    private final UserService userService;
    private final PlaylistLikeRepository playlistLikeRepository;
    private final PlaylistRepostRepository playlistRepostRepository;
    private final UserRepository userRepository;
    private final PlaylistTokenService playlistTokenService;

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------
    @Transactional
    public PlaylistResponse createPlaylist(Long userId, CreatePlaylistRequest request) {
        User user = userService.getUserIfExistsById(userId);

        String slug = SlugUtility.generateUniqueSlug(
                request.title(), playlistRepository::existsBySlug);

        String coverArtUrl = null;
        if (request.coverArt() != null && !request.coverArt().isEmpty()) {
            coverArtUrl = fileUtilityAzure.saveFile(request.coverArt(), FileType.TRACK_COVERS);
        }

        Playlist playlist = playlistMapper.toEntity(request, user, slug, coverArtUrl);
        playlist = playlistRepository.save(playlist);

        // If created as private, immediately issue a secret token
        if (request.isPrivate()) {
            playlistTokenService.issueNewToken(playlist);
        }

        return playlistMapper.toResponse(playlist);
    }

    // -------------------------------------------------------------------------
    // PATCH
    // -------------------------------------------------------------------------
    @Transactional
    public PlaylistResponse patchPlaylist(Long userId, Long playlistId, PatchPlaylistRequest request) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        // Snapshot visibility before applying patch to detect public → private transition
        boolean wasPublic = !playlist.isPrivate();
        boolean goingPrivate = Boolean.TRUE.equals(request.isPrivate());

        String newCoverArtUrl = null;

        playlistMapper.updateEntityFromPatch(request, playlist, newCoverArtUrl);
        playlist = playlistRepository.save(playlist);

        // Auto-issue a fresh token whenever visibility transitions public → private
        if (wasPublic && goingPrivate) {
            playlistTokenService.issueNewToken(playlist);
        }

        return playlistMapper.toResponse(playlist);
    }

    // -------------------------------------------------------------------------
    // GET — single playlist (any authenticated user, subject to privacy + block)
    // -------------------------------------------------------------------------
    public PlaylistResponse getPlaylist(Long playlistId, Long currentUserId) {
        Playlist playlist = findPlaylistById(playlistId);

        // Private playlist: only the owner can see it
        if (playlist.isPrivate()) {
            if (currentUserId == null || !playlist.getUser().getId().equals(currentUserId)) {
                throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
            }
        }

        // Block check (bidirectional)
        if (isUserBlocked(currentUserId, playlist.getUser().getId())) {
            throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
        }
        AccountTier userTier = AccountTier.FREE;

        if (currentUserId == null) {
            return playlistMapper.toResponse(playlist, Collections.emptySet(), Collections.emptySet(), userTier);
        }

        Set<Long> likedTrackIds = trackLikeRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> repostedTrackIds = trackRepostRepository.findTrackIdsByUserId(currentUserId);
        User currentUser = userService.getUserIfExistsById(currentUserId);
        userTier = currentUser.getTier();

        return playlistMapper.toResponse(playlist, likedTrackIds, repostedTrackIds, userTier);
    }

    // -------------------------------------------------------------------------
    // GET — via secret token (bypasses privacy, anyone with the link)
    // -------------------------------------------------------------------------
    public PlaylistResponse getPlaylistByToken(String token, Long currentUserId) {
        Playlist playlist = playlistTokenService.getPlaylistByToken(token);

        if (currentUserId == null) {
            return playlistMapper.toResponse(playlist);
        }

        Set<Long> likedTrackIds = trackLikeRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> repostedTrackIds = trackRepostRepository.findTrackIdsByUserId(currentUserId);
        User currentUser = userService.getUserIfExistsById(currentUserId);
        AccountTier userTier = currentUser.getTier();

        return playlistMapper.toResponse(playlist, likedTrackIds, repostedTrackIds, userTier);
    }

    // -------------------------------------------------------------------------
    // GET — public playlists of any user (by username)
    // -------------------------------------------------------------------------
    public Page<PlaylistResponse> getPublicPlaylistsByUsername(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        return playlistRepository
                .findByUserIdAndIsPrivateFalse(user.getId(), pageable)
                .map(playlistMapper::toResponse);
    }

    // -------------------------------------------------------------------------
    // GET — specific public playlist of any user (by username + playlistId)
    // -------------------------------------------------------------------------
    public PlaylistResponse getPublicPlaylistByIdAndUsername(
            String username, Long playlistId, Pageable trackPageable) {

        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        Playlist playlist = findPlaylistById(playlistId);

        if (!playlist.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
        }

        if (playlist.isPrivate()) {
            throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
        }

        Set<Long> trackLikes = currentUserId != null
                ? trackLikeRepository.findTrackIdsByUserId(currentUserId) : Collections.emptySet();
        Set<Long> trackReposts = currentUserId != null
                ? trackRepostRepository.findTrackIdsByUserId(currentUserId) : Collections.emptySet();

        User currentUser = userService.getUserIfExistsById(currentUserId);
        AccountTier userTier = currentUser.getTier();

        return playlistMapper.toResponse(playlist, trackLikes, trackReposts, userTier);
    }

    // -------------------------------------------------------------------------
    // GET — all playlists (public + private) of the current user
    // -------------------------------------------------------------------------
    public Page<PlaylistResponse> getPlaylistsByUserId(Long userId, Pageable pageable) {
        return playlistRepository
                .findByUserId(userId, pageable)
                .map(playlistMapper::toResponse);
    }

    // -------------------------------------------------------------------------
    // GET — current user's specific owned playlist (public or private)
    // -------------------------------------------------------------------------
    public PlaylistResponse getOwnedPlaylistById(Long currentUserId, Long playlistId, Pageable trackPageable) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, currentUserId);

        Set<Long> trackLikes = trackLikeRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> trackReposts = trackRepostRepository.findTrackIdsByUserId(currentUserId);

        User currentUser = userService.getUserIfExistsById(currentUserId);
        AccountTier userTier = currentUser.getTier();

        return playlistMapper.toResponse(playlist, trackLikes, trackReposts, userTier);
    }

    // -------------------------------------------------------------------------
    // GET — liked/reposted playlists of any user
    // -------------------------------------------------------------------------
    public Page<PlaylistResponse> getLikedPlaylistsByUsername(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        return playlistLikeRepository
                .findLikedPlaylistsByUserId(user.getId(), pageable)
                .map(playlistMapper::toResponse);
    }

    public Page<PlaylistResponse> getRepostedPlaylistsByUsername(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        return playlistRepostRepository
                .findRepostedPlaylistsByUserId(user.getId(), pageable)
                .map(playlistMapper::toResponse);
    }

    // -------------------------------------------------------------------------
    // TRACKS — ADD / REMOVE / REORDER
    // -------------------------------------------------------------------------
    @Transactional
    public PlaylistResponse addTrack(Long userId, Long playlistId, Long trackId) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));

        if (playlist.getTracks().stream().anyMatch(t -> t.getId().equals(trackId))) {
            throw new TrackAlreadyInPlaylistException(
                    "Track with ID " + trackId + " is already in this playlist.");
        }

        playlist.getTracks().add(track);
        playlist.setTrackCount(playlist.getTrackCount() + 1);
        playlist.setTotalDurationSeconds(
                playlist.getTotalDurationSeconds() + track.getDurationSeconds());

        // Merge genre from the added track
        if (track.getGenre() != null && !playlist.getGenres().contains(track.getGenre())) {
            playlist.getGenres().add(track.getGenre());
        }

        return playlistMapper.toResponse(playlistRepository.save(playlist));
    }

    @Transactional
    public void removeTrack(Long userId, Long playlistId, Long trackId) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));

        boolean removed = playlist.getTracks().removeIf(t -> t.getId().equals(trackId));
        if (!removed) {
            throw new ResourceNotFoundException("Track not found in playlist");
        }

        playlist.setTrackCount(Math.max(0, playlist.getTrackCount() - 1));
        playlist.setTotalDurationSeconds(
                Math.max(0, playlist.getTotalDurationSeconds() - track.getDurationSeconds()));

        // Recalculate genres from remaining tracks
        List<String> updatedGenres = playlist.getTracks().stream()
                .map(Track::getGenre)
                .filter(g -> g != null && !g.isBlank())
                .distinct()
                .collect(Collectors.toList());
        playlist.setGenres(updatedGenres);

        playlistRepository.save(playlist);
    }

    @Transactional
    public PlaylistResponse reorderTracks(Long playlistId, ReorderTracksRequest request, Long userId) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        List<Long> newOrder = request.trackIds();
        List<Long> existingIds = playlist.getTracks().stream()
                .map(Track::getId)
                .collect(Collectors.toList());

        if (!existingIds.containsAll(newOrder) || existingIds.size() != newOrder.size()) {
            throw new InvalidPlaylistOperationException(
                    "Provided track IDs must match exactly the tracks in the playlist");
        }

        Map<Long, Track> trackMap = playlist.getTracks().stream()
                .collect(Collectors.toMap(Track::getId, t -> t));

        playlist.setTracks(newOrder.stream().map(trackMap::get).collect(Collectors.toList()));

        return playlistMapper.toResponse(playlistRepository.save(playlist));
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------
    @Transactional
    public void deletePlaylist(Long playlistId, Long userId) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        String coverArtUrl = playlist.getCoverArtUrl();

        playlistLikeRepository.deleteAllByPlaylistId(playlistId);
        playlistRepostRepository.deleteAllByPlaylistId(playlistId);
        playlistRepository.delete(playlist);

        // Delete file from storage only AFTER the DB commit succeeds
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (coverArtUrl != null) {
                        fileUtilityAzure.deleteFileByUrl(coverArtUrl);
                    }
                } catch (Exception e) {
                    log.error("DB deletion succeeded but failed to delete cover art for playlist {}",
                            playlistId, e);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // COVER ART — explicit delete endpoint
    // -------------------------------------------------------------------------
    @Transactional
    public void deletePlaylistCover(Long playlistId, Long userId) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        if (playlist.getCoverArtUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(playlist.getCoverArtUrl());
            playlist.setCoverArtUrl(null);
            playlistRepository.save(playlist);
        }
    }

    // -------------------------------------------------------------------------
    // SECRET TOKEN — delegates to PlaylistTokenService (owner-only)
    // -------------------------------------------------------------------------
    /**
     * GET — returns the current active secret token for this playlist. Only the
     * owner can retrieve it if it is private
     */
    public PlaylistTokenResponse getToken(Long userId, Long playlistId) {
        Playlist playlist = findPlaylistById(playlistId);
        if (playlist.isPrivate() == true) {
            checkOwnership(playlist, userId);
            return playlistTokenService.getActiveToken(playlistId);
        } else {
            return playlistTokenService.getActiveToken(playlistId);
        }
    }

    @Transactional
    public void deletePlaylistCover(Long playlistId) {
        // 1. Fetch the playlist
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist not found"));

        // 2. Ownership Check: Ensure the person deleting is the creator
        Long currentUserId = JwtService.getCurrentUserId();
        if (!playlist.getUser().getId().equals(currentUserId)) {
            throw new UnauthorizedActionException("You do not have permission to edit this playlist.");
        }

        // 3. Delete from Azure and Update DB
        if (playlist.getCoverArtUrl() != null) {
            // Remove the actual file from storage
            fileUtilityAzure.deleteFileByUrl(playlist.getCoverArtUrl());

            // Nullify the reference in our database
            playlist.setCoverArtUrl(null);
            playlistRepository.save(playlist);
        }
    }

    /**
     * POST — generates (or regenerates) the secret token for this playlist.
     * Soft-deletes the previous token. Owner-only.
     */
    @Transactional
    public PlaylistTokenResponse generateToken(Long userId, Long playlistId) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);
        return playlistTokenService.regenerateToken(playlist);
    }

    // -------------------------------------------------------------------------
    // REPOST — unrepost (like is handled by LikeService)
    // -------------------------------------------------------------------------
    @Transactional
    public void unrepostPlaylist(Long userId, Long playlistId) {
        Playlist playlist = findPlaylistById(playlistId);

        PlaylistRepost repost = playlistRepostRepository.findByUserIdAndPlaylistId(userId, playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(
                "You have not reposted playlist with id " + playlistId));

        playlistRepostRepository.delete(repost);

        if (playlist.getRepostCount() > 0) {
            playlist.setRepostCount(playlist.getRepostCount() - 1);
            playlistRepository.save(playlist);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------
    private Playlist findPlaylistById(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(
                "Playlist with id " + playlistId + " not found"));
    }

    private void checkOwnership(Playlist playlist, Long userId) {
        if (!playlist.getUser().getId().equals(userId)) {
            throw new PlaylistAccessDeniedException(
                    "Access denied: You do not own this playlist.");
        }
    }

    private User getUserByUsername(String username) {
        return userService.getUserIfExistsByUsername(username);
    }

    private boolean isUserBlocked(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            return false;
        }
        return blockRepository.existsByBlocker_IdAndBlocked_Id(currentUserId, targetUserId)
                || blockRepository.existsByBlocker_IdAndBlocked_Id(targetUserId, currentUserId);
    }

    public Resource resolvePlaylistSlug(String slug) {
        Long id = playlistRepository.findIdBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                "No playlist found with slug: " + slug));

        return new Resource(ResourceType.PLAYLIST, id);
    }
}
