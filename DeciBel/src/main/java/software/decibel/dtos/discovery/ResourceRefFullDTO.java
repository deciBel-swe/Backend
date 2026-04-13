package software.decibel.dtos.discovery;

import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.track.TrackResponse;
import software.decibel.dtos.user.UserSummary;
import software.decibel.entities.User;
import software.decibel.mappers.UserMapper;

import java.time.LocalDateTime;

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

    public static ResourceRefFullDTO of(TrackResponse track, User repostedBy, LocalDateTime repostedAt) {
        return new ResourceRefFullDTO("TRACK", track, null, null, UserMapper.INSTANCE.toUserSummary(repostedBy), repostedAt);
    }

    public static ResourceRefFullDTO of(PlaylistResponse playlist, User repostedBy, LocalDateTime repostedAt) {
        return new ResourceRefFullDTO("PLAYLIST", null, playlist, null, UserMapper.INSTANCE.toUserSummary(repostedBy), repostedAt);
    }
}
