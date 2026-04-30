package software.decibel.dtos.discovery;

import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.track.responses.TrackResponse;
import software.decibel.dtos.user.UserSummaryDTO;

public record ResourceItemDto(
        String type,
        Long id,
        TrackResponse track,
        PlaylistResponse playlist,
        UserSummaryDTO user) {

    public static ResourceItemDto of(TrackResponse track) {
        return new ResourceItemDto("TRACK", track.id(), track, null, null);
    }

    public static ResourceItemDto of(PlaylistResponse playlist) {
        return new ResourceItemDto("PLAYLIST", playlist.id(), null, playlist, null);
    }

    public static ResourceItemDto of(UserSummaryDTO user) {
        return new ResourceItemDto("USER", user.id(), null, null, user);
    }
}
