package software.decibel.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import software.decibel.dtos.discovery.FeedPageResponse;
import software.decibel.dtos.discovery.ResourceRefFullDTO;
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.*;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final TrackRepository trackRepository;
    private final PlaylistRepository playlistRepository;
    private final FollowRepository followRepository;
    private final LikeService likeService;
    private final RepostService repostService;
    private final TrackMapper trackMapper;
    private final PlaylistMapper playlistMapper;

    public FeedPageResponse getFeed(User currentUser, Pageable pageable) {
        List<Long> followingIds = followRepository.findFollowingIdsByFollowerId(currentUser.getId());

        if (followingIds.isEmpty()) {
            return new FeedPageResponse(Collections.emptyList(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true);
        }

        // Fetch tracks and playlists from followed users
        // For now we fetch a Pageable-sized chunks of both and sort

        Page<Track> tracksPage = trackRepository.findByUploaderIdInAndVisibilityPublicAndPublishedTrue(followingIds, pageable);
        Page<Playlist> playlistsPage = playlistRepository.findByUserIdInAndIsPrivateFalse(followingIds, pageable);

        Set<Long> likedTrackIds = likeService.getLikedTrackIds(currentUser.getId());
        Set<Long> repostedTrackIds = repostService.getRepostedTrackIds(currentUser.getId());

        List<ResourceRefFullDTO> feedItems = Stream.concat(
                tracksPage.getContent().stream().map(t -> ResourceRefFullDTO.of(trackMapper.toTrackResponse(t, likedTrackIds, repostedTrackIds))),
                playlistsPage.getContent().stream().map(p -> ResourceRefFullDTO.of(playlistMapper.toResponse(p)))
        ).sorted(Comparator.comparing(this::getSortDate).reversed())
        .limit(pageable.getPageSize())
        .toList();

        long totalElements = tracksPage.getTotalElements() + playlistsPage.getTotalElements();
        int totalPages = (int) Math.ceil((double) totalElements / pageable.getPageSize());

        return new FeedPageResponse(
                feedItems,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                totalElements,
                totalPages,
                pageable.getPageNumber() >= totalPages - 1
        );
    }

    private LocalDateTime getSortDate(ResourceRefFullDTO dto) {
        if (dto.track() != null) {
            return dto.track().uploadDate().atStartOfDay();
        } else if (dto.playlist() != null) {
            return dto.playlist().createdAt();
        }
        return LocalDateTime.MIN;
    }
}
