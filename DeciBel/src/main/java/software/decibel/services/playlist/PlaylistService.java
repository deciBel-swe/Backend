package software.decibel.services.playlist;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistTokenResponse;
import software.decibel.dtos.playlist.ReorderTracksRequest;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistRepost;
import software.decibel.entities.PlaylistToken;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.FileType;
import software.decibel.exceptions.custom.InvalidPlaylistOperationException;
import software.decibel.exceptions.custom.PlaylistAccessDeniedException;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.TrackAlreadyInPlaylistException;
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

    @Transactional
    public PlaylistResponse createPlaylist(Long userId, CreatePlaylistRequest request) {
        User user = userService.getUserIfExistsById(userId);

        //Handle Business Logic (Slugs & Files)
        String slug = SlugUtility.generateUniqueSlug(
                request.title(),
                playlistRepository::existsBySlug);

        String coverArtUrl = null;
        if (request.coverArt() != null && !request.coverArt().isEmpty()) {
            coverArtUrl = fileUtilityAzure.saveFile(request.coverArt(), FileType.TRACK_COVERS);
        }

        // Delegate object creation to Mapper
        Playlist playlist = playlistMapper.toEntity(request, user, slug, coverArtUrl);

        playlist = playlistRepository.save(playlist);

        //Map back to Response DTO
        return playlistMapper.toResponse(playlist);
    }

    @Transactional
    public PlaylistResponse patchPlaylist(Long userId, Long playlistId, PatchPlaylistRequest request) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        String newSlug = null;
        String newCoverArtUrl = null;

        //  Handle File Business Logic if cover art changes
        if (request.coverArt() != null && !request.coverArt().isEmpty()) {
            if (playlist.getCoverArtUrl() != null) {
                fileUtilityAzure.deleteFileByUrl(playlist.getCoverArtUrl());
            }
            newCoverArtUrl = fileUtilityAzure.saveFile(request.coverArt(), FileType.TRACK_COVERS);
        }

        //Delegate the tedious field-by-field updates to the Mapper
        playlistMapper.updateEntityFromPatch(request, playlist, newSlug, newCoverArtUrl);

        playlist = playlistRepository.save(playlist);

        //Map back to Response DTO
        return playlistMapper.toResponse(playlist);
    }

    public PlaylistResponse getPlaylist(Long playlistId, Pageable trackPageable) {
        Long currentUserId = JwtService.getCurrentUserId();
        Playlist playlist = findPlaylistById(playlistId);

        // Privacy Check: Is it private and viewed by someone other than the owner?
        if (playlist.isPrivate()) {
            if (currentUserId == null || !playlist.getUser().getId().equals(currentUserId)) {
                throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
            }
        }

        // Block Check: Did the playlist owner block the current user, or vice versa?
        if (isUserBlocked(currentUserId, playlist.getUser().getId())) {
            throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
        }

        if (currentUserId == null) {
            // Guest viewing playlist
            return playlistMapper.toResponse(playlist, Collections.emptySet(), Collections.emptySet(), trackPageable);
        }

        // Fetch likes/reposts for logged-in user
        Set<Long> likedTrackIds = trackLikeRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> repostedTrackIds = trackRepostRepository.findTrackIdsByUserId(currentUserId);

        // Map and Paginate!
        return playlistMapper.toResponse(playlist, likedTrackIds, repostedTrackIds, trackPageable);
    }

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
        playlist.setTotalDurationSeconds(playlist.getTotalDurationSeconds() + track.getDurationSeconds());

        // Update genres from track
        if (track.getGenre() != null && !playlist.getGenres().contains(track.getGenre())) {
            playlist.getGenres().add(track.getGenre());
        }

        return playlistMapper.toResponse(playlistRepository.save(playlist));
    }

    @Transactional
    public void unrepostPlaylist(Long userId, Long playlistId) {
        //Ensure the playlist actually exists
        Playlist playlist = findPlaylistById(playlistId); // Uses the method we made earlier

        // Check if the user actually reposted it
        PlaylistRepost repost = playlistRepostRepository.findByUserIdAndPlaylistId(userId, playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(
                "You have not reposted playlist with id " + playlistId));

        //Delete the repost record
        playlistRepostRepository.delete(repost);

        //Decrement the playlist's repost counter (preventing negative numbers)
        if (playlist.getRepostCount() > 0) {
            playlist.setRepostCount(playlist.getRepostCount() - 1);
            playlistRepository.save(playlist);
        }
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

        return playlistMapper.toResponse(playlistRepository.save(playlist));
    }

    @Transactional
    public void deletePlaylist(Long playlistId) {
        // Fetch the playlist 
        Playlist playlist = getPlaylistIfExistsById(playlistId);

        // 2. Extract the file URL before we delete the database record
        String coverArtUrl = playlist.getCoverArtUrl();

        // Perform all database deletions for related entities
        playlistLikeRepository.deleteAllByPlaylistId(playlistId);
        playlistRepostRepository.deleteAllByPlaylistId(playlistId);

        // 4. Delete the playlist itself
        playlistRepository.delete(playlist);

        // 5. Instruct Spring to delete the files ONLY after the DB commit succeeds
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // Only run if the database successfully deletes the records
                    if (coverArtUrl != null) {
                        // Call your Azure utility directly using the URL we saved in step 2
                        fileUtilityAzure.deleteFileByUrl(coverArtUrl);
                    }
                } catch (Exception e) {
                    // The database deletion succeeded, but file deletion failed.
                    log.error("Database deletion succeeded, but failed to delete cover art for playlist {}", playlistId, e);
                }
            }
        });
    }

    //get playlist by token
    public PlaylistResponse getPlaylistByToken(String token) {
        PlaylistToken playlistToken = playlistTokenRepository
                .findByTokenAndIsDeletedFalse(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired playlist token"));
        return playlistMapper.toResponse(playlistToken.getPlaylist());
    }

    //generate token
    @Transactional
    public PlaylistTokenResponse generateToken(Long userId, Long playlistId) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        // Soft delete existing token
        playlistTokenRepository.findByPlaylistIdAndIsDeletedFalse(playlistId)
                .ifPresent(t -> {
                    t.setDeleted(true);
                    playlistTokenRepository.save(t);
                });

        String token = UUID.randomUUID().toString();
        playlistTokenRepository.save(PlaylistToken.builder()
                .token(token)
                .playlist(playlist)
                .build());

        return new PlaylistTokenResponse(token);
    }

// reorder tracks in playlist
    @Transactional
    public PlaylistResponse reorderTracks(Long userId, Long playlistId, ReorderTracksRequest request) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        List<Long> newOrder = request.trackIds();

        // Validate all provided IDs exist in playlist
        List<Long> existingIds = playlist.getTracks().stream()
                .map(Track::getId)
                .collect(Collectors.toList());

        if (!existingIds.containsAll(newOrder) || existingIds.size() != newOrder.size()) {
            throw new InvalidPlaylistOperationException(
                    "Provided track IDs must match exactly the tracks in the playlist");
        }

        // Reorder tracks
        Map<Long, Track> trackMap = playlist.getTracks().stream()
                .collect(Collectors.toMap(Track::getId, t -> t));

        List<Track> reordered = newOrder.stream()
                .map(trackMap::get)
                .collect(Collectors.toList());

        playlist.setTracks(reordered);
        return playlistMapper.toResponse(playlistRepository.save(playlist));
    }

    // public playlists of any user
    public Page<PlaylistResponse> getPublicPlaylistsByUsername(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        // Block check
        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        // Privacy check is handled by the repository: findByUserIdAndIsPrivateFalse
        return playlistRepository
                .findByUserIdAndIsPrivateFalse(user.getId(), pageable)
                .map(playlistMapper::toResponse);
    }

    // gets a specific public playlist of any user by playlist ID
    public PlaylistResponse getPublicPlaylistByIdAndUsername(String username, Long playlistId, Pageable trackPageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        // Block checking for the profile being viewed
        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        Playlist playlist = findPlaylistById(playlistId);

        // Ownership Check
        if (!playlist.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
        }

        // Privacy Check
        if (playlist.isPrivate()) {
            throw new ResourceNotFoundException("Playlist with id " + playlistId + " not found");
        }

        // Fetch likes and reposts for the CURRENT user
        Set<Long> trackLikes = currentUserId != null
                ? trackLikeRepository.findTrackIdsByUserId(currentUserId)
                : Collections.emptySet();

        Set<Long> trackReposts = currentUserId != null
                ? trackRepostRepository.findTrackIdsByUserId(currentUserId)
                : Collections.emptySet();

        return playlistMapper.toResponse(playlist, trackLikes, trackReposts, trackPageable);
    }

    // all playlists (including private) of the current user
    public Page<PlaylistResponse> getPlaylistsByUserId(Long userId, Pageable pageable) {
        return playlistRepository
                .findByUserId(userId, pageable)
                .map(playlistMapper::toResponse);
    }

    // getting current user's specific playlist by ID (private or public)
    public PlaylistResponse getOwnedPlaylistById(Long currentUserId, Long playlistId, Pageable trackPageable) {
        Playlist playlist = findPlaylistById(playlistId);

        // Since we already know the currentUserId (they own the playlist), we don't need JwtService here
        Set<Long> trackLikes = trackLikeRepository.findTrackIdsByUserId(currentUserId);
        Set<Long> trackReposts = trackRepostRepository.findTrackIdsByUserId(currentUserId);

        // Pass all 4 arguments to the mapper!
        return playlistMapper.toResponse(playlist, trackLikes, trackReposts, trackPageable);
    }

    // getting all user liked playlists
    public Page<PlaylistResponse> getLikedPlaylistsByUsername(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        // Block check
        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        return playlistLikeRepository
                .findLikedPlaylistsByUserId(user.getId(), pageable)
                .map(playlistMapper::toResponse);
    }

    //get all users who reposted a playlist
    public Page<PlaylistResponse> getRepostedPlaylistsByUsername(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        Long currentUserId = JwtService.getCurrentUserId();

        // Block check
        if (isUserBlocked(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User '" + username + "' not found");
        }

        return playlistRepostRepository
                .findRepostedPlaylistsByUserId(user.getId(), pageable)
                .map(playlistMapper::toResponse);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private Playlist findPlaylistById(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist with id " + playlistId + " not found"));
    }

    private void checkOwnership(Playlist playlist, Long userId) {
        if (!playlist.getUser().getId().equals(userId)) {
            // Throw the custom exception
            throw new PlaylistAccessDeniedException(
                    "Access denied: You do not own this playlist."
            );
        }
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User '" + username + "' not found"));
    }

    private boolean isUserBlocked(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            return false; // Guests can't be blocked in the traditional sense
        }
        boolean hasBlocked = blockRepository.existsByBlocker_IdAndBlocked_Id(currentUserId, targetUserId);
        boolean isBlockedBy = blockRepository.existsByBlocker_IdAndBlocked_Id(targetUserId, currentUserId);
        return hasBlocked || isBlockedBy;
    }

    @Transactional
    public void deletePlaylistCover(Long playlistId) {
        Playlist playlist = getPlaylistIfExistsById(playlistId);

        if (playlist.getCoverArtUrl() != null) {
            fileUtilityAzure.deleteFileByUrl(playlist.getCoverArtUrl());
            playlist.setCoverArtUrl(null);
            playlistRepository.save(playlist);
        }
    }

    private Playlist getPlaylistIfExistsById(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist with id " + playlistId + " not found"));
    }

}
