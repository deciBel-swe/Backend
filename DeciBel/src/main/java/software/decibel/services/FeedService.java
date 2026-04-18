package software.decibel.services;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.discovery.FeedPageResponse;
import software.decibel.dtos.discovery.ResourceRefFullDTO;
import software.decibel.entities.User;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistRepostRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;

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
    private final software.decibel.mappers.UserMapper userMapper;

    public FeedPageResponse getFeed(User currentUser, Pageable pageable) {
        List<Long> followingIds = followRepository.findFollowingIdsByFollowerId(currentUser.getId());

        if (followingIds.isEmpty()) {
            return new FeedPageResponse(Collections.emptyList(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true);
        }

        // Fetch reposts from followed users
        Page<software.decibel.entities.TrackRepost> trackRepostsPage = trackRepostRepository.findByUserIdIn(followingIds, pageable);
        Page<software.decibel.entities.PlaylistRepost> playlistRepostsPage = playlistRepostRepository.findByUserIdIn(followingIds, pageable);

        Set<Long> likedTrackIds = likeService.getLikedTrackIds(currentUser.getId());
        Set<Long> repostedTrackIds = repostService.getRepostedTrackIds(currentUser.getId());

        //  Extract to typed local variables to fix generic type inference
        Stream<ResourceRefFullDTO> trackStream = trackRepostsPage.getContent().stream()
                .map(tr -> ResourceRefFullDTO.of(
                trackMapper.toTrackResponse(tr.getTrack(), currentUser.getTier(), likedTrackIds, repostedTrackIds),
                userMapper.toUserSummary(tr.getUser()),
                tr.getRepostedAt()
        ));

        Stream<ResourceRefFullDTO> playlistStream = playlistRepostsPage.getContent().stream()
                .map(pr -> ResourceRefFullDTO.of(
                playlistMapper.toResponse(pr.getPlaylist()),
                userMapper.toUserSummary(pr.getUser()),
                pr.getRepostedAt()
        ));

        // Safely concatenate now that the compiler knows both are Stream<ResourceRefFullDTO>
        List<ResourceRefFullDTO> feedItems = Stream.concat(trackStream, playlistStream)
                .sorted(Comparator.comparing(ResourceRefFullDTO::repostedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
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
