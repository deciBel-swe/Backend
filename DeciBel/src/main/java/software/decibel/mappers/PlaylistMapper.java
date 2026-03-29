package software.decibel.mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;

@Component
public class PlaylistMapper {

    //create dto
    public Playlist toEntity(CreatePlaylistRequest request, User owner, String slug, String coverArtUrl) {
        return Playlist.builder()
                .title(request.title())
                .description(request.description())
                .type(request.type())
                .isPrivate(request.isPrivate())
                .user(owner)
                .slug(slug)
                .coverArtUrl(coverArtUrl)
                .trackCount(0) // Default for new playlist
                .totalDurationSeconds(0) // Default for new playlist
                .tracks(new ArrayList<>())
                .genres(new ArrayList<>())
                .build();
    }

    // 2. patch dto
    public void updateEntityFromPatch(PatchPlaylistRequest request, Playlist playlist, String newSlug, String newCoverArtUrl) {
        if (request.title() != null) {
            playlist.setTitle(request.title());
        }
        if (request.description() != null) {
            playlist.setDescription(request.description());
        }
        if (request.type() != null) {
            playlist.setType(request.type());
        }
        if (request.isPrivate() != null) {
            playlist.setPrivate(request.isPrivate());
        }

        // These are passed in from the service if they were updated
        if (newSlug != null) {
            playlist.setSlug(newSlug);
        }
        if (newCoverArtUrl != null) {
            playlist.setCoverArtUrl(newCoverArtUrl);
        }
    }

    //  3. ENTITY 
    public PlaylistResponse toResponse(Playlist playlist) {
        // Safely extract track IDs from the playlist's tracks
        List<Long> trackIds = new ArrayList<>();
        if (playlist.getTracks() != null) {
            trackIds = playlist.getTracks().stream()
                    .map(Track::getId)
                    .collect(Collectors.toList());
        }

        return new PlaylistResponse(
                playlist.getId(),
                playlist.getTitle(),
                playlist.getSlug(),
                playlist.getDescription(),
                playlist.getType(),
                playlist.isPrivate(),
                playlist.getCoverArtUrl(),
                playlist.getTrackCount(),
                playlist.getTotalDurationSeconds(),
                playlist.getGenres(),
                playlist.getUser() != null ? playlist.getUser().getId() : null,
                trackIds,
                playlist.getCreatedAt(),
                playlist.isLiked()
        );
    }
}
