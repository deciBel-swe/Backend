package software.decibel.mappers;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistSummaryResponse;
import software.decibel.dtos.track.TrackSummaryDTO;
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.services.playlist.PlaylistTokenService;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public abstract class PlaylistMapper {

    @Autowired
    protected TrackMapper trackMapper;

    @Autowired
    protected PlaylistTokenService playlistTokenService;

    public abstract Playlist toEntity(CreatePlaylistRequest request, User owner, String slug, String coverArtUrl);

    // ── V2 RESPONSE (PAGINATED) ────────────────────────────────────────────────
    @Mapping(target = "playlistSlug", source = "playlist.slug")
    @Mapping(target = "owner", source = "playlist.user")
    @Mapping(target = "isLiked", source = "isLikedPlaylist")
    @Mapping(target = "isReposted", source = "isRepostedPlaylist")
    @Mapping(target = "secretToken", expression = "java(secretToken)")
    @Mapping(target = "trackSummaryDto", expression = "java(mapTracksToPage(playlist.getTracks(), trackPageable, likedTrackIds, repostedTrackIds, accountTier))")
    @Mapping(target = "firstTrackWaveformUrl", expression = "java(playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0).getWaveformUrl())")
    public abstract PlaylistResponse toResponse(
            Playlist playlist,
            Set<Long> likedTrackIds,
            Set<Long> repostedTrackIds,
            boolean isLikedPlaylist,
            boolean isRepostedPlaylist,
            AccountTier accountTier,
            Pageable trackPageable,
            String secretToken);

    public PlaylistResponse toResponse(Playlist playlist, Pageable trackPageable, String secretToken) {
        return toResponse(playlist, Collections.emptySet(), Collections.emptySet(), false, false, AccountTier.FREE, trackPageable, secretToken);
    }

    // ── V1 RESPONSE (SUMMARY - FIRST 5) ─────────────────────────────────────────
    @Mapping(target = "playlistSlug", source = "playlist.slug")
    @Mapping(target = "owner", source = "playlist.user")
    @Mapping(target = "isLiked", source = "isLikedPlaylist")
    @Mapping(target = "isReposted", source = "isRepostedPlaylist")
    @Mapping(target = "secretToken", expression = "java(secretToken)")
    @Mapping(target = "trackSummaryDto", expression = "java(mapTracksToSummary(playlist.getTracks(), likedTrackIds, repostedTrackIds, accountTier))")
    @Mapping(target = "firstTrackWaveformUrl", expression = "java(playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0).getWaveformUrl())")
    public abstract PlaylistSummaryResponse toSummaryResponse(
            Playlist playlist,
            Set<Long> likedTrackIds,
            Set<Long> repostedTrackIds,
            boolean isLikedPlaylist,
            boolean isRepostedPlaylist,
            AccountTier accountTier,
            String secretToken);

    public PlaylistSummaryResponse toSummaryResponse(Playlist playlist, String secretToken) {
        return toSummaryResponse(playlist, Collections.emptySet(), Collections.emptySet(), false, false, AccountTier.FREE, secretToken);
    }

    // ── MAPPING HELPERS ───────────────────────────────────────────────────────
    protected Page<TrackSummaryDTO> mapTracksToPage(
            List<Track> tracks, Pageable pageable, Set<Long> likedTrackIds, Set<Long> repostedTrackIds, AccountTier tier) {
        if (tracks == null || tracks.isEmpty()) {
            return Page.empty(pageable);
        }

        List<TrackSummaryDTO> dtos = tracks.stream()
                .map(t -> trackMapper.toTrackSummaryDTO(t, likedTrackIds, repostedTrackIds, tier))
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), dtos.size());

        if (start > dtos.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, dtos.size());
        }

        return new PageImpl<>(dtos.subList(start, end), pageable, dtos.size());
    }

    protected List<TrackSummaryDTO> mapTracksToSummary(
            List<Track> tracks, Set<Long> likedTrackIds, Set<Long> repostedTrackIds, AccountTier tier) {
        if (tracks == null || trackMapper == null) {
            return Collections.emptyList();
        }
        return tracks.stream()
                .limit(5)
                .map(t -> trackMapper.toTrackSummaryDTO(t, likedTrackIds, repostedTrackIds, tier))
                .collect(Collectors.toList());
    }
}
