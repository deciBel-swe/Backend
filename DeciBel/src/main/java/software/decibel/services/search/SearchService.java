package software.decibel.services.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.discovery.ResourceItemDto;
import software.decibel.dtos.search.SearchResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.PlaylistLikeRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistRepostRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;
import software.decibel.services.playlist.PlaylistTokenService;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final TrackRepository trackRepository;
    private final PlaylistRepository playlistRepository;
    private final UserRepository userRepository;
    private final TrackMapper trackMapper;
    private final PlaylistMapper playlistMapper;
    private final UserMapper userMapper;
    private final PlaylistTokenService playlistTokenService;
    private final TrackLikeRepository trackLikeRepository;
    private final TrackRepostRepository trackRepostRepository;
    private final PlaylistLikeRepository playlistLikeRepository;
    private final PlaylistRepostRepository playlistRepostRepository;

    public SearchResponse search(String query, String type, int page, int size) {
        if (query == null || query.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must be at least 2 characters");
        }

        Pageable pageable = PageRequest.of(page, size);

        if (type == null || type.equalsIgnoreCase("all")) {
            return searchAll(query, pageable);
        }

        return switch (type.toLowerCase()) {
            case "track" ->
                searchTracks(query, pageable);
            case "playlist" ->
                searchPlaylists(query, pageable);
            case "user" ->
                searchUsers(query, pageable);
            case "tag" ->
                searchTags(query, pageable);
            default ->
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search type: " + type);
        };
    }

    private SearchResponse searchAll(String query, Pageable pageable) {
        Long userId = null;
        try {
            userId = JwtService.getCurrentUserId();
        } catch (Exception e) {
            // Unauthenticated
        }

        int pageSize = pageable.getPageSize();
        int pageNumber = pageable.getPageNumber();

        int tracksLimit = pageSize / 3 + (pageSize % 3 > 0 ? 1 : 0);
        int playlistsLimit = pageSize / 3 + (pageSize % 3 > 1 ? 1 : 0);
        int usersLimit = pageSize / 3;

        Pageable tracksPageable = PageRequest.of(pageNumber, tracksLimit);
        Pageable playlistsPageable = PageRequest.of(pageNumber, playlistsLimit);
        Pageable usersPageable = PageRequest.of(pageNumber, usersLimit);

        Page<Track> tracks = trackRepository.searchPublicTracks(query, userId, tracksPageable);
        Page<Playlist> playlists = playlistRepository.searchPublicPlaylists(query, userId, playlistsPageable);
        Page<User> users = userRepository.searchPublicUsers(query, userId, usersPageable);

        // Fetch like/repost data if user is logged in
        Set<Long> likedTrackIds = Collections.emptySet();
        Set<Long> repostedTrackIds = Collections.emptySet();
        Set<Long> likedPlaylistIds = Collections.emptySet();
        Set<Long> repostedPlaylistIds = Collections.emptySet();

        if (userId != null) {
            likedTrackIds = trackLikeRepository.findTrackIdsByUserId(userId);
            repostedTrackIds = trackRepostRepository.findTrackIdsByUserId(userId);
            likedPlaylistIds = playlistLikeRepository.findPlaylistIdsByUserId(userId);
            repostedPlaylistIds = playlistRepostRepository.findPlaylistIdsByUserId(userId);
        }
        final Set<Long> finalLikedTrackIds = likedTrackIds;
        final Set<Long> finalRepostedTrackIds = repostedTrackIds;
        final Set<Long> finalLikedPlaylistIds = likedPlaylistIds;
        final Set<Long> finalRepostedPlaylistIds = repostedPlaylistIds;

        List<ResourceItemDto> content = new ArrayList<>();
        tracks.getContent().forEach(t -> content.add(ResourceItemDto.of(
                trackMapper.toTrackResponse(t,
                        finalLikedTrackIds.contains(t.getId()),
                        finalRepostedTrackIds.contains(t.getId())))));

        playlists.getContent().forEach(p -> content.add(ResourceItemDto.of(
                playlistMapper.toSummaryResponse(p,
                        finalLikedTrackIds,
                        finalRepostedTrackIds,
                        finalLikedPlaylistIds.contains(p.getId()),
                        finalRepostedPlaylistIds.contains(p.getId()),
                        software.decibel.enums.AccountTier.FREE,
                        playlistTokenService.resolveToken(p.getId()))
        )));

        // CHANGED: Using toUserSummaryDto to ensure followerCount & trackCount populate
        users.getContent().forEach(u -> content.add(ResourceItemDto.of(userMapper.toUserSummaryDto(u))));

        long totalElements = tracks.getTotalElements() + playlists.getTotalElements() + users.getTotalElements();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        boolean isLast = tracks.isLast() && playlists.isLast() && users.isLast();

        return new SearchResponse(
                content,
                pageNumber,
                pageSize,
                totalElements,
                totalPages,
                isLast
        );
    }

    private SearchResponse searchTracks(String query, Pageable pageable) {
        Long userId = null;
        try {
            userId = JwtService.getCurrentUserId();
        } catch (Exception e) {
        }
        Page<Track> tracks = trackRepository.searchPublicTracks(query, userId, pageable);

        java.util.Set<Long> likedTrackIds = java.util.Collections.emptySet();
        java.util.Set<Long> repostedTrackIds = java.util.Collections.emptySet();
        if (userId != null) {
            likedTrackIds = trackLikeRepository.findTrackIdsByUserId(userId);
            repostedTrackIds = trackRepostRepository.findTrackIdsByUserId(userId);
        }

        final java.util.Set<Long> finalLikedTrackIds = likedTrackIds;
        final java.util.Set<Long> finalRepostedTrackIds = repostedTrackIds;

        List<ResourceItemDto> content = tracks.getContent().stream()
                .map(t -> ResourceItemDto.of(trackMapper.toTrackResponse(t,
                finalLikedTrackIds.contains(t.getId()),
                finalRepostedTrackIds.contains(t.getId()))))
                .collect(Collectors.toList());
        return toSearchResponse(tracks, content);
    }

    private SearchResponse searchPlaylists(String query, Pageable pageable) {
        Long userId = null;
        try {
            userId = JwtService.getCurrentUserId();
        } catch (Exception e) {
        }
        Page<Playlist> playlists = playlistRepository.searchPublicPlaylists(query, userId, pageable);

        java.util.Set<Long> likedPlaylistIds = java.util.Collections.emptySet();
        java.util.Set<Long> repostedPlaylistIds = java.util.Collections.emptySet();
        if (userId != null) {
            likedPlaylistIds = playlistLikeRepository.findPlaylistIdsByUserId(userId);
            repostedPlaylistIds = playlistRepostRepository.findPlaylistIdsByUserId(userId);
        }

        final Set<Long> finalLikedPlaylistIds = likedPlaylistIds;
        final Set<Long> finalRepostedPlaylistIds = repostedPlaylistIds;

        List<ResourceItemDto> content = playlists.getContent().stream()
                .map(p -> ResourceItemDto.of(playlistMapper.toSummaryResponse(p,
                java.util.Collections.emptySet(),
                java.util.Collections.emptySet(),
                finalLikedPlaylistIds.contains(p.getId()),
                finalRepostedPlaylistIds.contains(p.getId()),
                software.decibel.enums.AccountTier.FREE,
                playlistTokenService.resolveToken(p.getId()))))
                .collect(Collectors.toList());
        return toSearchResponse(playlists, content);
    }

    private SearchResponse searchUsers(String query, Pageable pageable) {
        Long userId = null;
        try {
            userId = JwtService.getCurrentUserId();
        } catch (Exception e) {
        }
        Page<User> users = userRepository.searchPublicUsers(query, userId, pageable);
        List<ResourceItemDto> content = users.getContent().stream()
                .map(u -> ResourceItemDto.of(userMapper.toUserSummaryDto(u)))
                .collect(Collectors.toList());
        return toSearchResponse(users, content);
    }

    private SearchResponse searchTags(String query, Pageable pageable) {
        Long userId = null;
        try {
            userId = JwtService.getCurrentUserId();
        } catch (Exception e) {
            // Unauthenticated
        }
        Page<Track> tracks = trackRepository.searchPublicTracksByTag(query, userId, pageable);

        java.util.Set<Long> likedTrackIds = java.util.Collections.emptySet();
        java.util.Set<Long> repostedTrackIds = java.util.Collections.emptySet();
        if (userId != null) {
            likedTrackIds = trackLikeRepository.findTrackIdsByUserId(userId);
            repostedTrackIds = trackRepostRepository.findTrackIdsByUserId(userId);
        }

        final java.util.Set<Long> finalLikedTrackIds = likedTrackIds;
        final java.util.Set<Long> finalRepostedTrackIds = repostedTrackIds;

        List<ResourceItemDto> content = tracks.getContent().stream()
                .map(t -> ResourceItemDto.of(trackMapper.toTrackResponse(t,
                finalLikedTrackIds.contains(t.getId()),
                finalRepostedTrackIds.contains(t.getId()))))
                .collect(Collectors.toList());
        return toSearchResponse(tracks, content);
    }

    private <T> SearchResponse toSearchResponse(Page<T> page, List<ResourceItemDto> content) {
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
