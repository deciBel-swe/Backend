package software.decibel.mappers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.OwnerDto;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistSummaryDto;
import software.decibel.dtos.track.TrackSummaryDTO;
import software.decibel.dtos.user.UserSummaryDTO;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistToken;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;

@Component
@RequiredArgsConstructor
public class PlaylistMapper {

    private static final int SUMMARY_TRACK_LIMIT = 5;

    private final TrackMapper trackMapper;
    private final UserMapper userMapper;

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------
    public Playlist toEntity(CreatePlaylistRequest request, User owner, String slug, String coverArtUrl) {
        return Playlist.builder()
                .title(request.title())
                .description(request.description())
                .type(request.type())
                .isPrivate(request.isPrivate())
                .user(owner)
                .slug(slug)
                .coverArtUrl(coverArtUrl)
                .trackCount(0)
                .totalDurationSeconds(0)
                .tracks(new ArrayList<>())
                .genres(new ArrayList<>())
                .build();
    }

    public void updateEntityFromPatch(
            PatchPlaylistRequest request,
            Playlist playlist,
            String newCoverArtUrl) {

        // title — no clear sentinel (blank title is invalid, null = keep existing)
        if (request.title() != null) {
            playlist.setTitle(request.title());
            // slug follows title — service passes newSlug when title is present
        } else {
            playlist.setTitle("");
        }

        // description — null = keep existing, non-null = overwrite
        if (request.description() != null) {
            playlist.setDescription(request.description());
        }

        // type — null = keep existing
        if (request.type() != null) {
            playlist.setType(request.type());
        } else {
            playlist.setType(null);
        }

        // isPrivate — null = keep existing, Boolean value = overwrite
        if (request.isPrivate() != null) {
            playlist.setPrivate(request.isPrivate());
        } else {
            playlist.setPrivate(false);
        }

        if (newCoverArtUrl != null) {
            playlist.setCoverArtUrl(newCoverArtUrl.isEmpty() ? null : newCoverArtUrl);
        }
    }

    // -------------------------------------------------------------------------
    // PLAYLIST RESPONSE
    // -------------------------------------------------------------------------
    public PlaylistResponse toResponse(
            Playlist playlist,
            Set<Long> likedTrackIds,
            Set<Long> repostedTrackIds,
            AccountTier accountTier) {

        List<Track> allTracks = playlist.getTracks() != null ? playlist.getTracks() : new ArrayList<>();

        List<TrackSummaryDTO> trackSummaries = allTracks.stream()
                .limit(SUMMARY_TRACK_LIMIT)
                .map(t -> trackMapper.toTrackSummaryDTO(t, likedTrackIds, repostedTrackIds, accountTier))
                .toList();

        String firstTrackWaveformUrl = allTracks.isEmpty() ? null : allTracks.get(0).getWaveformUrl();

        return new PlaylistResponse(
                playlist.getId(),
                playlist.getTitle(),
                playlist.getType(),
                playlist.isLiked(),
                playlist.getDescription(),
                playlist.isPrivate(),
                playlist.getCoverArtUrl(),
                playlist.getSlug(),
                playlist.getTotalDurationSeconds(),
                playlist.getTrackCount(),
                userMapper.toUserSummaryDto(playlist.getUser()),
                playlist.getGenres() != null ? playlist.getGenres() : new ArrayList<>(),
                playlist.getCreatedAt(),
                trackSummaries,
                firstTrackWaveformUrl);
    }

    public PlaylistResponse toResponse(Playlist playlist, Set<Long> likedTrackIds, Set<Long> repostedTrackIds) {
        return toResponse(playlist, likedTrackIds, repostedTrackIds, AccountTier.FREE);
    }

    public PlaylistResponse toResponse(Playlist playlist) {
        return toResponse(playlist, Collections.emptySet(), Collections.emptySet(), AccountTier.FREE);
    }

    // -------------------------------------------------------------------------
    // PLAYLIST SUMMARY DTO  (lightweight card — Image 3)
    // -------------------------------------------------------------------------
    public PlaylistSummaryDto toSummary(
            Playlist playlist,
            Set<Long> likedTrackIds,
            Set<Long> repostedTrackIds,
            AccountTier accountTier) {

        List<Track> allTracks = playlist.getTracks() != null ? playlist.getTracks() : new ArrayList<>();

        TrackSummaryDTO representativeTrack = allTracks.isEmpty()
                ? null
                : trackMapper.toTrackSummaryDTO(allTracks.get(0), likedTrackIds, repostedTrackIds, accountTier);

        String primaryGenre = (playlist.getGenres() != null && !playlist.getGenres().isEmpty())
                ? playlist.getGenres().get(0) : null;

        String secretToken = null;
        if (playlist.getSlugHistory() != null && !playlist.getSlugHistory().isEmpty()) {
            secretToken = playlist.getSlugHistory().stream()
                    .filter(t -> !t.isDeleted())
                    .map(PlaylistToken::getToken)
                    .findFirst()
                    .orElse(null);
        }

        return new PlaylistSummaryDto(
                playlist.getId(),
                playlist.getTitle(),
                playlist.getSlug(),
                playlist.isLiked(),
                playlist.isPrivate(),
                playlist.getCoverArtUrl(),
                playlist.getTrackCount(),
                buildOwnerSummaryDto(playlist.getUser()),
                primaryGenre,
                representativeTrack,
                secretToken);
    }

    public PlaylistSummaryDto toSummary(Playlist playlist) {
        return toSummary(playlist, Collections.emptySet(), Collections.emptySet(), AccountTier.FREE);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------
    private OwnerDto buildOwnerDto(User user) {
        if (user == null) {
            return new OwnerDto(null, null, null, null);
        }
        return new OwnerDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
    }

    private UserSummaryDTO buildOwnerSummaryDto(User user) {
        if (user == null) {
            return null;
        }
        return userMapper.toUserSummaryDto(user);
    }
}
