package software.decibel.mappers;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;
import org.springframework.data.domain.Page;
import software.decibel.dtos.track.TrackSummaryDTO;
import software.decibel.dtos.track.requests.TrackUploadRequest;
import software.decibel.dtos.track.responses.*;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.TrackAccess;
import software.decibel.enums.Visibility;

@Mapper(componentModel = "spring",
        uses = {UserMapper.class},
        imports = {Visibility.class}) // Spring injects it as a @Component
public interface TrackMapper {

    // ----------------- TrackResponse DTOs ---------------------
    @Mapping(target = "artist", expression = "java(mapArtist(track.getUploader()))")
    @Mapping(target = "tags", expression = "java(mapTags(track.getTags()))")
    @Mapping(target = "isLiked", expression = "java(likedTrackIds.contains(track.getId()))")
    @Mapping(target = "isReposted", expression = "java(repostedTrackIds.contains(track.getId()))")
    @Mapping(target = "trackDurationSeconds", source = "track.durationSeconds")
    @Mapping(target = "isPrivate", expression = "java(track.getVisibility() == Visibility.PRIVATE)")
    @Mapping(target = "playCount", source = "track.playCount")
    @Mapping(target = "completedPlayCount", source = "track.completedPlayCount")
    @Mapping(target = "commentCount", expression = "java(mapCommentCount(track))")
    @Mapping(target = "secretToken", expression = "java(mapSecretToken(track))")
    @Mapping(target = "access", expression = "java(resolveAccess(userTier, track.getAccess()))")
    @Mapping(target = "trackUrl", expression = "java(resolveTrackUrl(userTier, track))")
    @Mapping(target = "trackPreviewUrl", expression = "java(resolvePreviewUrl(userTier, track))")
    @Mapping(target = "trackSlug", expression = "java(track.getSlug())")
    TrackResponse toTrackResponse(
            Track track, AccountTier userTier, Set<Long> likedTrackIds, Set<Long> repostedTrackIds);

    // MapStruct to handle single track response
    @Mapping(target = "artist", expression = "java(mapArtist(track.getUploader()))")
    @Mapping(target = "tags", expression = "java(mapTags(track.getTags()))")
    @Mapping(target = "isLiked", expression = "java(isLiked)")
    @Mapping(target = "isReposted", expression = "java(isReposted)")
    @Mapping(target = "trackDurationSeconds", source = "track.durationSeconds")
    @Mapping(target = "isPrivate", expression = "java(track.getVisibility() == Visibility.PRIVATE)")
    @Mapping(target = "playCount", source = "track.playCount")
    @Mapping(target = "completedPlayCount", source = "track.completedPlayCount")
    @Mapping(target = "commentCount", expression = "java(mapCommentCount(track))")
    @Mapping(target = "secretToken", expression = "java(mapSecretToken(track))")
    @Mapping(target = "access", expression = "java(resolveAccess(userTier, track.getAccess()))")
    @Mapping(target = "trackUrl", expression = "java(resolveTrackUrl(userTier, track))")
    @Mapping(target = "trackPreviewUrl", expression = "java(resolvePreviewUrl(userTier, track))")
    @Mapping(target = "trackSlug", expression = "java(track.getSlug())")
    TrackResponse toTrackResponseSingle(Track track, AccountTier userTier, boolean isLiked, boolean isReposted);

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

    // For one track only (used when you fetch a single track and already know the booleans)
    @Mapping(target = "artist", expression = "java(mapArtist(track.getUploader()))")
    @Mapping(target = "tags", expression = "java(mapTags(track.getTags()))")
    @Mapping(target = "isLiked", expression = "java(isLiked)")
    @Mapping(target = "isReposted", expression = "java(isReposted)")
    @Mapping(target = "trackDurationSeconds", source = "track.durationSeconds")
    @Mapping(target = "isPrivate", expression = "java(track.getVisibility() == Visibility.PRIVATE)")
    @Mapping(target = "playCount", source = "track.playCount")
    @Mapping(target = "commentCount", expression = "java(mapCommentCount(track))")
    @Mapping(target = "access", expression = "java(resolveAccess(userTier, track.getAccess()))")
    @Mapping(target = "secretToken", expression = "java(mapSecretToken(track))")
    @Mapping(target = "trackUrl", expression = "java(resolveTrackUrl(userTier, track))")
    @Mapping(target = "trackPreviewUrl", expression = "java(resolvePreviewUrl(userTier, track))")
    @Mapping(target = "trackSlug", source = "track.slug")
    TrackResponse toTrackResponse(Track track, boolean isLiked, boolean isReposted, AccountTier userTier);

    
    default int mapCommentCount(Track track) {
        if (track == null || track.getComments() == null) {
            return 0;
        }
        return track.getComments().size();
    }

    default String mapSecretToken(Track track) {
        if (track == null || track.getTokens() == null || track.getTokens().isEmpty()) {
            return null;
        }
        return track.getTokens().stream()
                .filter(token -> !token.isDeleted())
                .map(token -> token.getToken())
                .findFirst()
                .orElse(null);
    }

    default TrackArtist mapArtist(User user) {
        if (user == null) {
            return null;
        }

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
    @Mapping(target = "tokens", ignore = true)
    @Mapping(target = "comments", ignore = true)
    Track toEntity(TrackUploadRequest dto, User uploader);

    // ----------------- TrackStatus DTOs ---------------------
    // Track -> TrackStatusResponse DTO
    @Mapping(source = "id", target = "trackId")
    @Mapping(source = "state", target = "trackState")
    @Mapping(target = "progressPercentage", ignore = true)
    @Mapping(target = "stepName", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "trackResponse", ignore = true)
    TrackStatusResponse toTrackStatusResponse(Track track);

    // --------------- TrackPatch DTOs ---------------------
    // Track -> TrackPatchResponse DTO
    @Mapping(
            target = "isPrivate",
            expression = "java(track.getVisibility() == software.decibel.enums.Visibility.PRIVATE)")
    @Mapping(target = "tags", expression = "java(mapTags(track.getTags()))")
    @Mapping(target = "trackSlug", source = "slug")
    @Mapping(target = "commentCount", expression = "java(mapCommentCount(track))")
    @Mapping(target = "secretToken", expression = "java(mapSecretToken(track))")
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

    // --------------------TrackSummary DTOs -----------------
    // track -> track summary
    @Mapping(target = "trackSlug", source = "slug")
    @Mapping(target = "artist", source = "uploader")
    @Mapping(target = "trackPreviewUrl", source = "trackPreviewUrl")
    @Mapping(target = "isLiked", ignore = true)
    @Mapping(target = "isReposted", ignore = true)
    @Mapping(target = "secretToken", ignore = true)
    TrackSummaryDTO toTrackSummary(Track track);

    default TrackSummaryDTO toTrackSummaryDTO(
            Track track,
            Set<Long> likedTrackIds,
            Set<Long> repostedTrackIds,
            AccountTier accountTier) {
        TrackSummaryDTO dto = toTrackSummary(track);
        if (dto == null) {
            return null;
        }

        return new TrackSummaryDTO(
                dto.id(),
                dto.title(),
                dto.trackSlug(),
                dto.coverUrl(),
                resolveTrackUrl(accountTier, track),
                resolvePreviewUrl(accountTier, track),
                dto.artist(),
                dto.playCount(),
                dto.likeCount(),
                dto.repostCount(),
                dto.commentCount(),
                likedTrackIds != null && likedTrackIds.contains(track.getId()),
                repostedTrackIds != null && repostedTrackIds.contains(track.getId()),
                mapSecretToken(track),
                resolveAccess(accountTier, track.getAccess()));
    }
}
