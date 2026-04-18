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

    public SearchResponse search(String query, String type, int page, int size) {
        if (query == null || query.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must be at least 2 characters");
        }

        Pageable pageable = PageRequest.of(page, size);

        if (type == null || type.equalsIgnoreCase("all")) {
            return searchAll(query, pageable);
        }

        return switch (type.toLowerCase()) {
            case "track" -> searchTracks(query, pageable);
            case "playlist" -> searchPlaylists(query, pageable);
            case "user" -> searchUsers(query, pageable);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search type: " + type);
        };
    }

    private SearchResponse searchAll(String query, Pageable pageable) {
        // For "all", we might want to combine results. 
        // A simple way is to take a few from each, but here we'll just search everything and combine.
        // However, standard search usually paginates. 
        // If we want a global search like feed, we might need a more complex query or just combine.
        
        Page<Track> tracks = trackRepository.searchPublicTracks(query, pageable);
        Page<Playlist> playlists = playlistRepository.searchPublicPlaylists(query, pageable);
        Page<User> users = userRepository.searchPublicUsers(query, pageable);

        List<ResourceRefFullDTO> content = new ArrayList<>();
        tracks.getContent().forEach(t -> content.add(ResourceRefFullDTO.of(trackMapper.toTrackResponse(t, false, false))));
        playlists.getContent().forEach(p -> content.add(ResourceRefFullDTO.of(playlistMapper.toResponse(p))));
        users.getContent().forEach(u -> content.add(ResourceRefFullDTO.of(userMapper.toUserSummary(u))));

        // This "all" search is a bit naive for pagination as it's not truly global pagination across types.
        // But for many apps, it's acceptable to just return combined results from first pages of each.
        
        return new SearchResponse(
                content,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                tracks.getTotalElements() + playlists.getTotalElements() + users.getTotalElements(),
                Math.max(Math.max(tracks.getTotalPages(), playlists.getTotalPages()), users.getTotalPages()),
                tracks.isLast() && playlists.isLast() && users.isLast()
        );
    }

    private SearchResponse searchTracks(String query, Pageable pageable) {
        Page<Track> tracks = trackRepository.searchPublicTracks(query, pageable);
        List<ResourceRefFullDTO> content = tracks.getContent().stream()
                .map(t -> ResourceRefFullDTO.of(trackMapper.toTrackResponse(t, false, false)))
                .collect(Collectors.toList());
        return toSearchResponse(tracks, content);
    }

    private SearchResponse searchPlaylists(String query, Pageable pageable) {
        Page<Playlist> playlists = playlistRepository.searchPublicPlaylists(query, pageable);
        List<ResourceRefFullDTO> content = playlists.getContent().stream()
                .map(p -> ResourceRefFullDTO.of(playlistMapper.toResponse(p)))
                .collect(Collectors.toList());
        return toSearchResponse(playlists, content);
    }

    private SearchResponse searchUsers(String query, Pageable pageable) {
        Page<User> users = userRepository.searchPublicUsers(query, pageable);
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
