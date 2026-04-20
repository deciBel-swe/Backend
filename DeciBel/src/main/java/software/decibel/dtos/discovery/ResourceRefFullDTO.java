package software.decibel.dtos.discovery;

import java.time.LocalDateTime;

import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.track.responses.TrackResponse;
import software.decibel.dtos.user.UserSummary;

public record ResourceRefFullDTO(
        String type,
        TrackResponse track,
        PlaylistResponse playlist,
        UserSummary user,
        UserSummary repostedBy,
        LocalDateTime repostedAt) {

    public static ResourceRefFullDTO of(TrackResponse track) {
        return new ResourceRefFullDTO("TRACK", track, null, null, null, null);
    }

    public static ResourceRefFullDTO of(PlaylistResponse playlist) {
        return new ResourceRefFullDTO("PLAYLIST", null, playlist, null, null, null);
    }

    public static ResourceRefFullDTO of(UserSummary user) {
        return new ResourceRefFullDTO("USER", null, null, user, null, null);
    }

    public static ResourceRefFullDTO of(TrackResponse track, UserSummary repostedBy, LocalDateTime repostedAt) {
        return new ResourceRefFullDTO("TRACK", track, null, null, repostedBy, repostedAt);
    }

    public static ResourceRefFullDTO of(PlaylistResponse playlist, UserSummary repostedBy, LocalDateTime repostedAt) {
        return new ResourceRefFullDTO("PLAYLIST", null, playlist, null, repostedBy, repostedAt);
    }
}
