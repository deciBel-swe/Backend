package software.decibel.mappers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import software.decibel.dtos.discovery.StationPageResponse;
import software.decibel.dtos.track.TrackSummaryDTO;
import software.decibel.entities.Track;

@Mapper(
        componentModel = "spring",
        uses = {TrackMapper.class})
// can use TrackMapper (needed in track entity -> TrackSummaryDTO)
public interface StationMapper {

    // default because im dealing with pages
    // From a page of tracks and a set of user's liked tracks ids and reposted tracks ids
    // we have a map between track ids and their secret token that we also use
    default StationPageResponse toPageResponse(
            Page<Track> page, Set<Long> likedIds, Set<Long> repostedIds, Map<Long, String> tokenMap) {

        // i turn page's content into a stream and generate tracksummarydto for each track
        // i also mark inside the track summary dto if the track is liked or reposted by user
        List<TrackSummaryDTO> content
                = page.getContent().stream()
                        .map(
                                track -> {
                                    TrackSummaryDTO dto = toTrackSummary(track);
                                    return new TrackSummaryDTO(
                                            dto.id(),
                                            dto.title(),
                                            dto.trackSlug(),
                                            dto.coverUrl(),
                                            dto.trackUrl(),
                                            dto.trackPreviewUrl(),
                                            dto.artist(),
                                            dto.playCount(),
                                            dto.likeCount(),
                                            dto.repostCount(),
                                            dto.commentCount(),
                                            likedIds != null && likedIds.contains(track.getId()),
                                            repostedIds != null && repostedIds.contains(track.getId()),
                                            tokenMap.getOrDefault(track.getId(), null),// secretToken
                                            dto.access()
                                    );
                                })
                        .toList();

        return new StationPageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    // delegates to TrackMapper.toTrackSummary via `uses`
    TrackSummaryDTO toTrackSummary(Track track);
}
