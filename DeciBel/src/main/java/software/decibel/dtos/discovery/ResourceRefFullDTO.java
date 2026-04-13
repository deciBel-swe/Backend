package software.decibel.dtos.discovery;

import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.track.TrackResponse;
import software.decibel.dtos.user.UserSummary;

public record ResourceRefFullDTO(
        String type,
        TrackResponse track,
        PlaylistResponse playlist,
        UserSummary user) {

    public static ResourceRefFullDTO of(TrackResponse track) {
        return new ResourceRefFullDTO("TRACK", track, null, null);
    }

    public static ResourceRefFullDTO of(PlaylistResponse playlist) {
        return new ResourceRefFullDTO("PLAYLIST", null, playlist, null);
    }

    public static ResourceRefFullDTO of(UserSummary user) {
        return new ResourceRefFullDTO("USER", null, null, user);
    }
}
