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
    private final TrackRepostRepository trackRepostRepository;
    private final PlaylistRepostRepository playlistRepostRepository;
    private final LikeService likeService;
    private final RepostService repostService;
    private final TrackMapper trackMapper;
    private final PlaylistMapper playlistMapper;

    public FeedPageResponse getFeed(User currentUser, Pageable pageable) {
        List<Long> followingIds = followRepository.findFollowingIdsByFollowerId(currentUser.getId());

        System.out.println("DEBUG: Current user ID: " + currentUser.getId());
        System.out.println("DEBUG: Following IDs: " + followingIds);

        if (followingIds.isEmpty()) {
            return new FeedPageResponse(Collections.emptyList(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true);
        }

        // Fetch reposts from followed users
        Page<software.decibel.entities.TrackRepost> trackRepostsPage = trackRepostRepository.findByUserIdIn(followingIds, pageable);
        Page<software.decibel.entities.PlaylistRepost> playlistRepostsPage = playlistRepostRepository.findByUserIdIn(followingIds, pageable);

        System.out.println("DEBUG: Track reposts found: " + trackRepostsPage.getTotalElements());
        System.out.println("DEBUG: Playlist reposts found: " + playlistRepostsPage.getTotalElements());

        Set<Long> likedTrackIds = likeService.getLikedTrackIds(currentUser.getId());
        Set<Long> repostedTrackIds = repostService.getRepostedTrackIds(currentUser.getId());

        List<ResourceRefFullDTO> feedItems = Stream.concat(
                trackRepostsPage.getContent().stream().map(tr -> ResourceRefFullDTO.of(
                    trackMapper.toTrackResponse(tr.getTrack(), likedTrackIds, repostedTrackIds),
                    tr.getUser(),
                    tr.getRepostedAt()
                )),
                playlistRepostsPage.getContent().stream().map(pr -> ResourceRefFullDTO.of(
                    playlistMapper.toResponse(pr.getPlaylist()),
                    pr.getUser(),
                    pr.getRepostedAt()
                ))
        ).sorted(Comparator.comparing(ResourceRefFullDTO::repostedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
        .limit(pageable.getPageSize())
        .toList();

        long totalElements = trackRepostsPage.getTotalElements() + playlistRepostsPage.getTotalElements();
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

}
