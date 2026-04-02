package software.decibel.mappers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;
import org.springframework.data.domain.Page;
import software.decibel.dtos.track.*;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;

@Mapper(componentModel = "spring") // Spring injects it as a @Component
public interface TrackMapper {

    // ----------------- TrackResponse DTOs ---------------------
    // MapStruct handles this fully - no default needed
    @Mapping(target = "artist", expression = "java(mapArtist(track.getUploader()))")
    @Mapping(target = "tags", expression = "java(mapTags(track.getTags()))")
    @Mapping(target = "isLiked", expression = "java(likedTrackIds.contains(track.getId()))")
    @Mapping(target = "isReposted", expression = "java(repostedTrackIds.contains(track.getId()))")
    TrackResponse toTrackResponse(Track track, Set<Long> likedTrackIds, Set<Long> repostedTrackIds);

    // This method perfectly handles your paginated views using the Sets passed from the Service
    default TrackPageResponse toPageResponse(
            Page<Track> page, Set<Long> likedTrackIds, Set<Long> repostedTrackIds) {
        return new TrackPageResponse(
                page.getContent().stream()
                        .map(track -> toTrackResponse(track, likedTrackIds, repostedTrackIds))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    // For one track only (used when you fetch a single track and already know the booleans)
    @Mapping(target = "artist", expression = "java(mapArtist(track.getUploader()))")
    @Mapping(target = "tags", expression = "java(mapTags(track.getTags()))")
    @Mapping(target = "isLiked", expression = "java(isLiked)")
    @Mapping(target = "isReposted", expression = "java(isReposted)")
    TrackResponse toTrackResponse(Track track, boolean isLiked, boolean isReposted);

    default TrackArtist mapArtist(User user) {
        if (user == null) {
            return null;
        }
        return new TrackArtist(user.getId(), user.getUsername(), user.getAvatarUrl());
    }

    // ----------------- TrackUpload DTOs ---------------------
    // Track -> TrackUploadResponse DTO
    TrackUploadResponse toTrackUploadResponse(Track track);

    // TrackUploadRequest DTO → Track
    @Mapping(target = "trackUrl", ignore = true)
    @Mapping(target = "coverUrl", ignore = true)
    @Mapping(target = "waveformUrl", ignore = true)
    @Mapping(target = "durationSeconds", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "published", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(
            target = "visibility",
            expression
            = "java(dto.isPrivate() ? software.decibel.enums.Visibility.PRIVATE : software.decibel.enums.Visibility.PUBLIC)")
    @Mapping(target = "uploader", source = "uploader")
    Track toEntity(TrackUploadRequest dto, User uploader);

    // ----------------- TrackStatus DTOs ---------------------
    // Track -> TrackStatusResponse DTO
    @Mapping(source = "id", target = "trackId")
    @Mapping(source = "state", target = "trackState")
    TrackStatusResponse toTrackStatusResponse(Track track);

    // --------------- TrackPatch DTOs ---------------------
    // Track -> TrackPatchResponse DTO
    @Mapping(
            target = "isPrivate",
            expression = "java(track.getVisibility() == software.decibel.enums.Visibility.PRIVATE)")
    @Mapping(target = "tags", expression = "java(mapTags(track.getTags()))")
    TrackPatchResponse toTrackPatchResponse(Track track);

    default List<String> mapTags(List<Tag> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream().map(Tag::getTitle).collect(Collectors.toList());
    }

    // --------------- TrackWaveFormUrl DTOs ---------------------
    // Request -> track
    @Mapping(target = "trackId", source = "id")
    @Mapping(target = "duration", source = "durationSeconds")
    TrackWaveFormUrlResponse toTrackWaveFormUrlResponse(Track track);

    // --------------- TrackPublish DTOs ---------------------
    // track -> response
    @Mapping(target = "publishedAt", source = "publishedAt")
    TrackPublishResponse toTrackPublishResponse(Track track);
}
