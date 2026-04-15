package software.decibel.mappers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;
import org.springframework.data.domain.Page;
import software.decibel.dtos.track.requests.TrackUploadRequest;
import software.decibel.dtos.track.responses.*;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.TrackAccess;
import software.decibel.enums.Visibility;

@Mapper(componentModel = "spring",
        imports = {Visibility.class}) // Spring injects it as a @Component
public interface TrackMapper {

  // ----------------- TrackResponse DTOs ---------------------

  @Mapping(target = "artist", expression = "java(mapArtist(track.getUploader()))")
  @Mapping(target = "tags", expression = "java(mapTags(track.getTags()))")
  @Mapping(target = "isLiked", expression = "java(likedTrackIds.contains(track.getId()))")
  @Mapping(target = "isReposted", expression = "java(repostedTrackIds.contains(track.getId()))")
  @Mapping(target = "trackDurationSeconds", source = "track.durationSeconds")
  @Mapping(target = "isPrivate", expression = "java(track.getVisibility() == Visibility.PRIVATE)")
  @Mapping(target = "access", expression = "java(resolveAccess(userTier, track.getAccess()))")
  @Mapping(target = "trackUrl", expression = "java(resolveTrackUrl(userTier, track))")
  @Mapping(target = "trackPreviewUrl", expression = "java(resolvePreviewUrl(userTier, track))")
  TrackResponse toTrackResponse(
      Track track, AccountTier userTier, Set<Long> likedTrackIds, Set<Long> repostedTrackIds);

  // ----------------- Single Track Response ---------------------

  @Mapping(target = "artist", expression = "java(mapArtist(track.getUploader()))")
  @Mapping(target = "tags", expression = "java(mapTags(track.getTags()))")
  @Mapping(target = "isLiked", source = "isLiked")
  @Mapping(target = "isReposted", source = "isReposted")
  @Mapping(target = "trackDurationSeconds", source = "track.durationSeconds")
  @Mapping(target = "isPrivate", expression = "java(track.getVisibility() == Visibility.PRIVATE)")
  @Mapping(target = "access", expression = "java(resolveAccess(userTier, track.getAccess()))")
  @Mapping(target = "trackUrl", expression = "java(resolveTrackUrl(userTier, track))")
  @Mapping(target = "trackPreviewUrl", expression = "java(resolvePreviewUrl(userTier, track))")
  TrackResponse toTrackResponseSingle(
      Track track, AccountTier userTier, boolean isLiked, boolean isReposted);

  // ----------------- Page mapping ---------------------

  default TrackPageResponse toPageResponse(
      Page<Track> page, AccountTier userTier, Set<Long> likedTrackIds, Set<Long> repostedTrackIds) {

    return new TrackPageResponse(
        page.getContent().stream()
            .map(track -> toTrackResponse(track, userTier, likedTrackIds, repostedTrackIds))
            .toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isLast());
    }

  // ----------------- Artist mapping ---------------------

  default TrackArtist mapArtist(User user) {
    if (user == null) return null;

    return new TrackArtist(
        user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
  }

  // ----------------- ACCESS BUSINESS RULES ---------------------

  default TrackAccess resolveAccess(AccountTier tier, TrackAccess access) {

    if (access == null) {
      access = TrackAccess.BLOCKED;
    }

    // PRO override
    if (tier == AccountTier.PRO) {
      if (access == TrackAccess.BLOCKED) {
        return TrackAccess.PLAYABLE;
      }
      return access; // playable and preview same
    }

    // free users
    if (access == TrackAccess.PLAYABLE) {
      return TrackAccess.BLOCKED; // playable becomes blocked
    }

    return access;
  }

  // ----------------- URL RESOLVERS ---------------------

  default String resolveTrackUrl(AccountTier tier, Track track) {

    TrackAccess resolved = resolveAccess(tier, track.getAccess());

    if (resolved == TrackAccess.PLAYABLE) {
      return track.getTrackUrl();
    }

    return null;
  }

  default String resolvePreviewUrl(AccountTier tier, Track track) {

    TrackAccess resolved = resolveAccess(tier, track.getAccess());

    if (resolved == TrackAccess.PREVIEW) {
      return track.getTrackPreviewUrl();
    }

    return null;
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
