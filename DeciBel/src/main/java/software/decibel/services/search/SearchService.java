package software.decibel.services.search;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.discovery.ResourceRefFullDTO;
import software.decibel.dtos.search.SearchResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final TrackRepository trackRepository;
    private final PlaylistRepository playlistRepository;
    private final UserRepository userRepository;
    private final TrackMapper trackMapper;
    private final PlaylistMapper playlistMapper;
    private final UserMapper userMapper;
    private final software.decibel.services.engagement.LikeService likeService;
    private final software.decibel.services.engagement.RepostService repostService;
    private final software.decibel.repositories.BlockRepository blockRepository;

    /**
     * Entry point for global search.
     * UPDATED: Now detects current user to personalize results (likes/reposts) and enforce blocking.
     */
    public SearchResponse search(String query, String type, int page, int size) {
        if (query == null || query.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must be at least 2 characters");
        }

        Long currentUserId = software.decibel.services.JwtService.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);

        if (type == null || type.equalsIgnoreCase("all")) {
            return searchAll(query, pageable, currentUserId);
        }

        return switch (type.toLowerCase()) {
            case "track" -> searchTracks(query, pageable, currentUserId);
            case "playlist" -> searchPlaylists(query, pageable, currentUserId);
            case "user" -> searchUsers(query, pageable, currentUserId);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search type: " + type);
        };
    }

    /**
     * Performs a combined search across tracks, playlists, and users.
     * UPDATED: Personalizes results and filters out blocked content.
     */
    private SearchResponse searchAll(String query, Pageable pageable, Long currentUserId) {
        int pageSize = pageable.getPageSize();
        int pageNumber = pageable.getPageNumber();

        int tracksLimit = pageSize / 3 + (pageSize % 3 > 0 ? 1 : 0);
        int playlistsLimit = pageSize / 3 + (pageSize % 3 > 1 ? 1 : 0);
        int usersLimit = pageSize / 3;

        Pageable tracksPageable = PageRequest.of(pageNumber, tracksLimit);
        Pageable playlistsPageable = PageRequest.of(pageNumber, playlistsLimit);
        Pageable usersPageable = PageRequest.of(pageNumber, usersLimit);

        // Fetch results using block-aware repository methods
        Page<Track> tracks = trackRepository.searchPublicTracksWithBlocking(query, currentUserId, tracksPageable);
        Page<Playlist> playlists = playlistRepository.searchPublicPlaylistsWithBlocking(query, currentUserId, playlistsPageable);
        Page<User> users = userRepository.searchPublicUsersWithBlocking(query, currentUserId, usersPageable);

        // Fetch current user details and interactions for personalization
        User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        software.decibel.enums.AccountTier tier = currentUser != null ? currentUser.getTier() : software.decibel.enums.AccountTier.FREE;
        java.util.Set<Long> likedTrackIds = currentUserId != null ? likeService.getLikedTrackIds(currentUserId) : java.util.Collections.emptySet();
        java.util.Set<Long> repostedTrackIds = currentUserId != null ? repostService.getRepostedTrackIds(currentUserId) : java.util.Collections.emptySet();

        List<ResourceRefFullDTO> content = new ArrayList<>();
        
        // Map results with proper user state
        tracks.getContent().forEach(t -> content.add(ResourceRefFullDTO.of(
            trackMapper.toTrackResponse(t, tier, likedTrackIds, repostedTrackIds)
        )));
        
        playlists.getContent().forEach(p -> content.add(ResourceRefFullDTO.of(playlistMapper.toResponse(p))));
        users.getContent().forEach(u -> content.add(ResourceRefFullDTO.of(userMapper.toUserSummary(u))));

        long totalElements = tracks.getTotalElements() + playlists.getTotalElements() + users.getTotalElements();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        boolean isLast = tracks.isLast() && playlists.isLast() && users.isLast();

        return new SearchResponse(content, pageNumber, pageSize, totalElements, totalPages, isLast);
    }

    /**
     * Search only tracks.
     * UPDATED: Personalized liked/reposted status.
     */
    private SearchResponse searchTracks(String query, Pageable pageable, Long currentUserId) {
        Page<Track> tracks = trackRepository.searchPublicTracksWithBlocking(query, currentUserId, pageable);
        
        User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        software.decibel.enums.AccountTier tier = currentUser != null ? currentUser.getTier() : software.decibel.enums.AccountTier.FREE;
        java.util.Set<Long> likedTrackIds = currentUserId != null ? likeService.getLikedTrackIds(currentUserId) : java.util.Collections.emptySet();
        java.util.Set<Long> repostedTrackIds = currentUserId != null ? repostService.getRepostedTrackIds(currentUserId) : java.util.Collections.emptySet();

        List<ResourceRefFullDTO> content = tracks.getContent().stream()
                .map(t -> ResourceRefFullDTO.of(
                    trackMapper.toTrackResponse(t, tier, likedTrackIds, repostedTrackIds)
                ))
                .collect(Collectors.toList());
        return toSearchResponse(tracks, content);
    }

    /**
     * Search only playlists.
     * UPDATED: Block-aware search.
     */
    private SearchResponse searchPlaylists(String query, Pageable pageable, Long currentUserId) {
        Page<Playlist> playlists = playlistRepository.searchPublicPlaylistsWithBlocking(query, currentUserId, pageable);
        List<ResourceRefFullDTO> content = playlists.getContent().stream()
                .map(p -> ResourceRefFullDTO.of(playlistMapper.toResponse(p)))
                .collect(Collectors.toList());
        return toSearchResponse(playlists, content);
    }

    /**
     * Search only users.
     * UPDATED: Block-aware search.
     */
    private SearchResponse searchUsers(String query, Pageable pageable, Long currentUserId) {
        Page<User> users = userRepository.searchPublicUsersWithBlocking(query, currentUserId, pageable);
        List<ResourceRefFullDTO> content = users.getContent().stream()
                .map(u -> ResourceRefFullDTO.of(userMapper.toUserSummary(u)))
                .collect(Collectors.toList());
        return toSearchResponse(users, content);
    }

    private <T> SearchResponse toSearchResponse(Page<T> page, List<ResourceRefFullDTO> content) {
        return new SearchResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
