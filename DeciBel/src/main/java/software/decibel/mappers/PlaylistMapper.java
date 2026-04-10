package software.decibel.mappers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.OwnerDto;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;

@Component
@RequiredArgsConstructor
public class PlaylistMapper {

    private final TrackMapper trackMapper;

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
                .trackCount(0)
                .totalDurationSeconds(0)
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

    // 3. response dto 
    public PlaylistResponse toResponse(Playlist playlist, Set<Long> likedTrackIds, Set<Long> repostedTrackIds, Pageable trackPageable) {

        List<Track> allTracks = playlist.getTracks() != null ? playlist.getTracks() : new ArrayList<>();

        // 1. Safely paginate the list of tracks in-memory
        int start = (int) trackPageable.getOffset();
        int end = Math.min((start + trackPageable.getPageSize()), allTracks.size());

        List<Track> pagedTracks = new ArrayList<>();
        if (start < allTracks.size()) {
            pagedTracks = allTracks.subList(start, end);
        }

        // 2. Create the Spring Page object
        Page<Track> trackPage = new PageImpl<>(pagedTracks, trackPageable, allTracks.size());

        // 3. Map it using the method already inside your TrackMapper!
        TrackPageResponse trackPageResponse = trackMapper.toPageResponse(trackPage, likedTrackIds, repostedTrackIds);

        // Safely extract user variables
        Long userId = null;
        String username = null;
        String displayName = null;
        String avatarUrl = null;
        if (playlist.getUser() != null) {
            userId = playlist.getUser().getId();
            username = playlist.getUser().getUsername();
            displayName = playlist.getUser().getDisplayName();
            avatarUrl = playlist.getUser().getAvatarUrl();
        }

        OwnerDto ownerDto = new OwnerDto(userId, username, displayName, avatarUrl);

        return new PlaylistResponse(
                playlist.getId(),
                playlist.getTitle(),
                playlist.getType(),
                playlist.isLiked(),
                playlist.getDescription(),
                playlist.isPrivate(),
                playlist.getCoverArtUrl(),
                playlist.getTotalDurationSeconds(),
                playlist.getTrackCount(),
                ownerDto,
                playlist.getGenres(),
                playlist.getCreatedAt(),
                trackPageResponse
        );
    }

    //Fallback method: Use this for Guest users (not logged in)
    public PlaylistResponse toResponse(Playlist playlist, Set<Long> likedTrackIds, Set<Long> repostedTrackIds) {
        // Defaults to Page 0, Size 20
        return toResponse(playlist, likedTrackIds, repostedTrackIds, PageRequest.of(0, 20));
    }

    public PlaylistResponse toResponse(Playlist playlist) {
        return toResponse(playlist, Collections.emptySet(), Collections.emptySet(), PageRequest.of(0, 20));
    }
}
