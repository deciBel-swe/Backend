package software.decibel.services.playlist;

import java.util.ArrayList;
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
import software.decibel.dtos.playlist.PlaylistSummaryResponse;
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
import software.decibel.mappers.PlaylistMapper;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.PlaylistLikeRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistRepostRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserService;
import software.decibel.utils.FileUtilityAzure;
import software.decibel.utils.SlugUtility;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final TrackRepository trackRepository;
    private final TrackLikeRepository trackLikeRepository;
    private final TrackRepostRepository trackRepostRepository;
    private final BlockRepository blockRepository;
    private final FileUtilityAzure fileUtilityAzure;
    private final PlaylistMapper playlistMapper;
    private final UserService userService;
    private final PlaylistLikeRepository playlistLikeRepository;
    private final PlaylistRepostRepository playlistRepostRepository;
    private final PlaylistTokenService playlistTokenService;

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------
    @Transactional
    public PlaylistSummaryResponse createPlaylist(Long userId, CreatePlaylistRequest request) {
        Playlist playlist = processCreatePlaylist(userId, request);
        return playlistMapper.toSummaryResponse(playlist, resolveSecretTokenForUser(playlist));
    }

    @Transactional
    public PlaylistResponse createPlaylistV2(Long userId, CreatePlaylistRequest request, Pageable pageable) {
        Playlist playlist = processCreatePlaylist(userId, request);
        return playlistMapper.toResponse(playlist, pageable, resolveSecretTokenForUser(playlist));
    }

    // -------------------------------------------------------------------------
    // PATCH
    // -------------------------------------------------------------------------
    @Transactional
    public PlaylistSummaryResponse patchPlaylist(Long userId, Long playlistId, PatchPlaylistRequest request) {
        Playlist playlist = processPatchPlaylist(userId, playlistId, request);
        return mapToSummaryWithEngagement(playlist, userId);
    }

    @Transactional
    public PlaylistResponse patchPlaylistV2(Long userId, Long playlistId, PatchPlaylistRequest request, Pageable pageable) {
        Playlist playlist = processPatchPlaylist(userId, playlistId, request);
        return mapToResponseWithEngagement(playlist, userId, pageable);
    }

    // -------------------------------------------------------------------------
    // GET — single playlist (any authenticated user, subject to privacy + block)
    // -------------------------------------------------------------------------
    public PlaylistSummaryResponse getPlaylist(Long playlistId, Long currentUserId) {
        Playlist playlist = fetchPlaylistForReading(playlistId, currentUserId);
        return mapToSummaryWithEngagement(playlist, currentUserId);
    }

    public PlaylistResponse getPlaylistV2(Long playlistId, Long currentUserId, Pageable pageable) {
        Playlist playlist = fetchPlaylistForReading(playlistId, currentUserId);
        return mapToResponseWithEngagement(playlist, currentUserId, pageable);
    }

    // -------------------------------------------------------------------------
    // GET — via secret token (bypasses privacy, anyone with the link)
    // -------------------------------------------------------------------------
    public PlaylistSummaryResponse getPlaylistByToken(String token, Long currentUserId) {
        Playlist playlist = playlistTokenService.getPlaylistByToken(token); // Or your existing fetch logic
        return mapToSummaryWithEngagement(playlist, currentUserId);
    }

    public PlaylistResponse getPlaylistByTokenV2(String token, Long currentUserId, Pageable pageable) {
        Playlist playlist = playlistTokenService.getPlaylistByToken(token); // Or your existing fetch logic
        return mapToResponseWithEngagement(playlist, currentUserId, pageable);
    }

    // -------------------------------------------------------------------------
    // GET — public playlists of any user (by username)
    // -------------------------------------------------------------------------
    public PlaylistSummaryResponse getPublicPlaylistByIdAndUsername(String username, Long playlistId) {
        Playlist playlist = fetchPublicPlaylistByIdAndUsername(username, playlistId);
        // Note: Pass currentUserId instead of null if your controller supports an authenticated reader here
        return mapToSummaryWithEngagement(playlist, null);
    }

    public PlaylistResponse getPublicPlaylistByIdAndUsernameV2(String username, Long playlistId, Pageable pageable) {
        Playlist playlist = fetchPublicPlaylistByIdAndUsername(username, playlistId);
        return mapToResponseWithEngagement(playlist, null, pageable);
    }

    public Page<PlaylistSummaryResponse> getPlaylistsByUserId(Long userId, Pageable pageable) {
        Long currentUserId = JwtService.getCurrentUserId();
        Page<Playlist> playlists = playlistRepository.findByUserId(userId, pageable);
        if (currentUserId == null) {
            return playlists.map(p -> playlistMapper.toSummaryResponse(p, null));
        }

        // Fetch what the CURRENT user has liked/reposted
        Set<Long> likedPIds = playlistLikeRepository.findPlaylistIdsByUserId(currentUserId);
        Set<Long> repostedPIds = playlistRepostRepository.findPlaylistIdsByUserId(currentUserId);

        // You also need track engagement if you want the track summaries inside to be accurate
        Set<Long> likedTIds = trackLikeRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> repostedTIds = trackRepostRepository.findTrackIdsByUserId(currentUserId);

        User currentUser = userService.getUserIfExistsById(currentUserId);

        return playlists.map(p -> playlistMapper.toSummaryResponse(
                p,
                likedTIds,
                repostedTIds,
                likedPIds.contains(p.getId()),
                repostedPIds.contains(p.getId()),
                currentUser.getTier(),
                resolveSecretTokenForUser(p)
        ));
    }

    public Page<PlaylistSummaryResponse> getPublicPlaylistsByUsername(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        Page<Playlist> playlists = playlistRepository.findByUserIdAndIsPrivateFalse(user.getId(), pageable);

        if (currentUserId == null) {
            return playlists.map(playlist -> playlistMapper.toSummaryResponse(playlist, resolveSecretTokenForUser(playlist)));
        }

        // IF LOGGED IN: Fetch engagement
        Set<Long> likedTrackIds = trackLikeRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> repostedTrackIds = trackRepostRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> likedPlaylistIds = playlistLikeRepository.findPlaylistIdsByUserId(currentUserId);
        Set<Long> repostedPlaylistIds = playlistRepostRepository.findPlaylistIdsByUserId(currentUserId);

        User currentUser = userService.getUserIfExistsById(currentUserId);
        AccountTier accountTier = currentUser.getTier();

        return playlists.map(playlist -> playlistMapper.toSummaryResponse(
                playlist,
                likedTrackIds,
                repostedTrackIds,
                likedPlaylistIds.contains(playlist.getId()),
                repostedPlaylistIds.contains(playlist.getId()),
                accountTier,
                resolveSecretTokenForUser(playlist)
        ));
    }
    // -------------------------------------------------------------------------
    // GET — current user's specific owned playlist (public or private)
    // -------------------------------------------------------------------------

    public PlaylistSummaryResponse getOwnedPlaylistById(Long userId, Long playlistId) {
        Playlist playlist = fetchOwnedPlaylistById(userId, playlistId);
        return mapToSummaryWithEngagement(playlist, userId);
    }

    public PlaylistResponse getOwnedPlaylistByIdV2(Long userId, Long playlistId, Pageable pageable) {
        Playlist playlist = fetchOwnedPlaylistById(userId, playlistId);
        return mapToResponseWithEngagement(playlist, userId, pageable);
    }

    public Page<PlaylistSummaryResponse> getLikedPlaylistsByUsername(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        Page<Playlist> playlists = playlistLikeRepository.findLikedPlaylistsByUserId(user.getId(), pageable);

        if (currentUserId == null) {
            return playlists.map(playlist -> playlistMapper.toSummaryResponse(playlist, resolveSecretTokenForUser(playlist)));
        }

        Set<Long> likedTrackIds = trackLikeRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> repostedTrackIds = trackRepostRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> likedPlaylistIds = playlistLikeRepository.findPlaylistIdsByUserId(currentUserId);
        Set<Long> repostedPlaylistIds = playlistRepostRepository.findPlaylistIdsByUserId(currentUserId);
        User currentUser = userService.getUserIfExistsById(currentUserId);
        AccountTier accountTier = currentUser.getTier();

        return playlists.map(playlist -> playlistMapper.toSummaryResponse(
                playlist,
                likedTrackIds,
                repostedTrackIds,
                likedPlaylistIds.contains(playlist.getId()),
                repostedPlaylistIds.contains(playlist.getId()),
                accountTier,
                resolveSecretTokenForUser(playlist)
        ));
    }

    public Page<PlaylistSummaryResponse> getRepostedPlaylistsByUsername(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        Page<Playlist> playlists = playlistRepostRepository.findRepostedPlaylistsByUserId(user.getId(), pageable);

        if (currentUserId == null) {
            return playlists.map(playlist -> playlistMapper.toSummaryResponse(playlist, resolveSecretTokenForUser(playlist)));
        }

        Set<Long> likedTrackIds = trackLikeRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> repostedTrackIds = trackRepostRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> likedPlaylistIds = playlistLikeRepository.findPlaylistIdsByUserId(currentUserId);
        Set<Long> repostedPlaylistIds = playlistRepostRepository.findPlaylistIdsByUserId(currentUserId);
        User currentUser = userService.getUserIfExistsById(currentUserId);
        AccountTier accountTier = currentUser.getTier();

        return playlists.map(playlist -> playlistMapper.toSummaryResponse(
                playlist,
                likedTrackIds,
                repostedTrackIds,
                likedPlaylistIds.contains(playlist.getId()),
                repostedPlaylistIds.contains(playlist.getId()),
                accountTier,
                resolveSecretTokenForUser(playlist)
        ));
    }

    // -------------------------------------------------------------------------
    // TRACKS — ADD / REMOVE / REORDER
    // -------------------------------------------------------------------------
    @Transactional
    public PlaylistSummaryResponse addTrack(Long userId, Long playlistId, Long trackId) {
        Playlist playlist = processAddTrack(userId, playlistId, trackId);
        return mapToSummaryWithEngagement(playlist, userId);
    }

    @Transactional
    public PlaylistResponse addTrackV2(Long userId, Long playlistId, Long trackId, Pageable pageable) {
        Playlist playlist = processAddTrack(userId, playlistId, trackId);
        return mapToResponseWithEngagement(playlist, userId, pageable);
    }

    @Transactional
    public PlaylistResponse removeTrack(Long userId, Long playlistId, Long trackId) {
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

        String secretToken = resolveSecretTokenForUser(playlist);
        return playlistMapper.toResponse(playlistRepository.saveAndFlush(playlist), Pageable.ofSize(20), secretToken);
    }

    @Transactional
    public PlaylistSummaryResponse reorderTracks(Long userId, Long playlistId, ReorderTracksRequest request) {
        Playlist playlist = processReorderTracks(userId, playlistId, request);
        return mapToSummaryWithEngagement(playlist, userId);
    }

    @Transactional
    public PlaylistResponse reorderTracksV2(Long userId, Long playlistId, ReorderTracksRequest request, Pageable pageable) {
        Playlist playlist = processReorderTracks(userId, playlistId, request);
        return mapToResponseWithEngagement(playlist, userId, pageable);
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
        if (playlist.isPrivate()) {
            checkOwnership(playlist, userId);
        }
        return playlistTokenService.getActiveToken(playlistId);
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

    public String resolveSecretTokenForUser(Playlist playlist) {
        return playlistTokenService.resolveToken(playlist.getId());
    }

    private Playlist processCreatePlaylist(Long userId, CreatePlaylistRequest request) {
        User user = userService.getUserIfExistsById(userId);
        String slug = SlugUtility.generateUniqueSlug(request.title(), playlistRepository::existsBySlug);

        String coverArtUrl = null;
        if (request.coverArt() != null && !request.coverArt().isEmpty()) {
            coverArtUrl = fileUtilityAzure.saveFile(request.coverArt(), FileType.TRACK_COVERS);
        }
        Playlist playlist = playlistMapper.toEntity(request, user, slug, coverArtUrl);
        playlist.setTracks(new java.util.ArrayList<>());
        playlist.setGenres(new java.util.ArrayList<>());
        playlist = playlistRepository.saveAndFlush(playlist);
        playlistTokenService.issueNewToken(playlist);
        return playlist;
    }

    private Playlist fetchPlaylistForReading(Long playlistId, Long currentUserId) {
        Playlist playlist = findPlaylistById(playlistId);
        if (playlist.isPrivate() && (currentUserId == null || !playlist.getUser().getId().equals(currentUserId))) {
            throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
        }
        if (isUserBlocked(currentUserId, playlist.getUser().getId())) {
            throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
        }
        return playlist;
    }

    private PlaylistSummaryResponse mapToSummaryWithEngagement(Playlist playlist, Long currentUserId) {
        String token = resolveSecretTokenForUser(playlist);
        if (currentUserId == null) {
            return playlistMapper.toSummaryResponse(playlist, token);
        }

        User currentUser = userService.getUserIfExistsById(currentUserId);
        return playlistMapper.toSummaryResponse(playlist,
                trackLikeRepository.findTrackIdsByUserId(currentUserId),
                trackRepostRepository.findTrackIdsByUserId(currentUserId),
                playlistLikeRepository.existsByUserAndPlaylist(currentUser, playlist),
                playlistRepostRepository.existsByUserAndPlaylist(currentUser, playlist),
                currentUser.getTier(), token);
    }

    private PlaylistResponse mapToResponseWithEngagement(Playlist playlist, Long currentUserId, Pageable pageable) {
        String token = resolveSecretTokenForUser(playlist);
        if (currentUserId == null) {
            return playlistMapper.toResponse(playlist, pageable, token);
        }

        User currentUser = userService.getUserIfExistsById(currentUserId);
        return playlistMapper.toResponse(playlist,
                trackLikeRepository.findTrackIdsByUserId(currentUserId),
                trackRepostRepository.findTrackIdsByUserId(currentUserId),
                playlistLikeRepository.existsByUserAndPlaylist(currentUser, playlist),
                playlistRepostRepository.existsByUserAndPlaylist(currentUser, playlist),
                currentUser.getTier(), pageable, token);
    }

    private Playlist fetchPublicPlaylistByIdAndUsername(String username, Long playlistId) {
        User user = getUserByUsername(username);
        Playlist playlist = findPlaylistById(playlistId);
        if (!playlist.getUser().getId().equals(user.getId()) || playlist.isPrivate()) {
            throw new ResourceNotFoundException("Playlist not found or is private");
        }
        return playlist;
    }

    private Playlist fetchOwnedPlaylistById(Long userId, Long playlistId) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);
        return playlist;
    }

    private Playlist processPatchPlaylist(Long userId, Long playlistId, PatchPlaylistRequest request) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        // Snapshot visibility before applying patch to detect public → private transition
        boolean wasPublic = !playlist.isPrivate();
        boolean goingPrivate = Boolean.TRUE.equals(request.isPrivate());

        // MANUALLY UPDATE FIELDS ONLY IF THEY ARE PRESENT IN THE REQUEST
        if (request.title() != null) {
            playlist.setTitle(request.title());
        }

        if (request.description() != null) {
            playlist.setDescription(request.description());
        }

        if (request.type() != null) {
            playlist.setType(request.type());
        }

        if (request.isPrivate() != null) {
            playlist.setPrivate(request.isPrivate());
        }

        // Handle cover art upload separately
        if (request.coverArt() != null && !request.coverArt().isEmpty()) {
            String newCoverArtUrl = fileUtilityAzure.saveFile(request.coverArt(), FileType.TRACK_COVERS);
            playlist.setCoverArtUrl(newCoverArtUrl);
        }

        // Auto-issue a fresh token whenever visibility transitions public → private
        if (wasPublic && goingPrivate) {
            playlistTokenService.issueNewToken(playlist);
        }

        //String secretToken = resolveSecretTokenForUser(playlist, userId);
        return playlistRepository.saveAndFlush(playlist);
    }

    private Playlist processAddTrack(Long userId, Long playlistId, Long trackId) {
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

        String secretToken = resolveSecretTokenForUser(playlist);
        return playlistRepository.saveAndFlush(playlist);
    }

    private Playlist processReorderTracks(Long userId, Long playlistId, ReorderTracksRequest request) {
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

        String secretToken = resolveSecretTokenForUser(playlist);
        return playlist;
    }
}