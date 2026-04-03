package software.decibel.services.playlist;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistSlug;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.FileType;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistSlugRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.user.UserService;
import software.decibel.utils.FileUtilityAzure;
import software.decibel.utils.SlugUtility;

@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistSlugRepository playlistSlugRepository;
    private final TrackRepository trackRepository;
    private final FileUtilityAzure fileUtilityAzure;
    private final PlaylistMapper playlistMapper;
    private final UserService userService;

    @Transactional
    public PlaylistResponse createPlaylist(Long userId, CreatePlaylistRequest request) {
        User user = userService.getUserIfExistsById(userId);

        // 1. Handle Business Logic (Slugs & Files)
        String slug = SlugUtility.generateUniqueSlug(
                request.title(),
                playlistRepository::existsBySlug);

        String coverArtUrl = null;
        if (request.coverArt() != null && !request.coverArt().isEmpty()) {
            coverArtUrl = fileUtilityAzure.saveFile(request.coverArt(), FileType.TRACK_COVERS);
        }

        // 2. Delegate object creation to Mapper
        Playlist playlist = playlistMapper.toEntity(request, user, slug, coverArtUrl);

        playlist = playlistRepository.save(playlist);

        // 3. Save slug to history
        playlistSlugRepository.save(PlaylistSlug.builder()
                .slug(slug)
                .playlist(playlist)
                .build());

        // 4. Map back to Response DTO
        return playlistMapper.toResponse(playlist);
    }

    @Transactional
    public PlaylistResponse patchPlaylist(Long userId, Long playlistId, PatchPlaylistRequest request) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        String newSlug = null;
        String newCoverArtUrl = null;

        // 1. Handle Slug Business Logic if title changes
        if (request.title() != null) {
            playlistSlugRepository.findBySlugAndIsDeletedFalse(playlist.getSlug())
                    .ifPresent(s -> {
                        s.setDeleted(true);
                        playlistSlugRepository.save(s);
                    });

            newSlug = SlugUtility.generateUniqueSlug(
                    request.title(),
                    playlistRepository::existsBySlug);

            playlistSlugRepository.save(PlaylistSlug.builder()
                    .slug(newSlug)
                    .playlist(playlist)
                    .build());
        }

        // 2. Handle File Business Logic if cover art changes
        if (request.coverArt() != null && !request.coverArt().isEmpty()) {
            if (playlist.getCoverArtUrl() != null) {
                fileUtilityAzure.deleteFileByUrl(playlist.getCoverArtUrl());
            }
            newCoverArtUrl = fileUtilityAzure.saveFile(request.coverArt(), FileType.TRACK_COVERS);
        }

        // 3. Delegate the tedious field-by-field updates to the Mapper
        playlistMapper.updateEntityFromPatch(request, playlist, newSlug, newCoverArtUrl);

        playlist = playlistRepository.save(playlist);

        // 4. Map back to Response DTO
        return playlistMapper.toResponse(playlist);
    }

    public PlaylistResponse getPlaylist(Long playlistId) {
        return playlistMapper.toResponse(findPlaylistById(playlistId));
    }

    @Transactional
    public PlaylistResponse addTrack(Long userId, Long playlistId, Long trackId) {
        Playlist playlist = findPlaylistById(playlistId);
        checkOwnership(playlist, userId);

        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));

        if (playlist.getTracks().stream().anyMatch(t -> t.getId().equals(trackId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Track already in playlist");
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

    // ── Helpers ──────────────────────────────────────────────────────────────
    private Playlist findPlaylistById(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist with id " + playlistId + " not found"));
    }

    private void checkOwnership(Playlist playlist, Long userId) {
        if (!playlist.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this playlist");
        }
    }

}
