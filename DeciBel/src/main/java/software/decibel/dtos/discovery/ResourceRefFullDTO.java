package software.decibel.dtos.discovery;

import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.track.TrackResponse;
import software.decibel.dtos.user.UserSummaryDTO;

import java.time.LocalDateTime;

public record ResourceRefFullDTO(
        String type,
        TrackResponse track,
        PlaylistResponse playlist,
        UserSummaryDTO user,
        UserSummaryDTO repostedBy,
        LocalDateTime repostedAt) {

    public static ResourceRefFullDTO of(TrackResponse track) {
        return new ResourceRefFullDTO("TRACK", track, null, null, null, null);
    }

    public static ResourceRefFullDTO of(PlaylistResponse playlist) {
        return new ResourceRefFullDTO("PLAYLIST", null, playlist, null, null, null);
    }

    public static ResourceRefFullDTO of(UserSummaryDTO user) {
        return new ResourceRefFullDTO("USER", null, null, user, null, null);
    }

    public static ResourceRefFullDTO of(TrackResponse track, UserSummaryDTO repostedBy, LocalDateTime repostedAt) {
        return new ResourceRefFullDTO("TRACK", track, null, null, repostedBy, repostedAt);
    }

    public static ResourceRefFullDTO of(PlaylistResponse playlist, UserSummaryDTO repostedBy, LocalDateTime repostedAt) {
        return new ResourceRefFullDTO("PLAYLIST", null, playlist, null, repostedBy, repostedAt);
    }
}
