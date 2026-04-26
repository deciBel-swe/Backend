package software.decibel.services.search;

import java.util.ArrayList;
import java.util.List;
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
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;

import software.decibel.services.JwtService;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final TrackRepository trackRepository;
    private final PlaylistRepository playlistRepository;
    private final UserRepository userRepository;
    private final TrackMapper trackMapper;
    private final PlaylistMapper playlistMapper;
    private final UserMapper userMapper;

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

        List<ResourceItemDto> content = new ArrayList<>();
        tracks.getContent().forEach(t -> content.add(ResourceItemDto.of(trackMapper.toTrackResponse(t, false, false))));
        playlists.getContent().forEach(p -> content.add(ResourceItemDto.of(playlistMapper.toResponse(p))));
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
        List<ResourceItemDto> content = tracks.getContent().stream()
                .map(t -> ResourceItemDto.of(trackMapper.toTrackResponse(t, false, false)))
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
        List<ResourceItemDto> content = playlists.getContent().stream()
                .map(p -> ResourceItemDto.of(playlistMapper.toResponse(p)))
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
