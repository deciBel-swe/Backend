package software.decibel.utils;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import software.decibel.entities.Track;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.JwtService;
import software.decibel.services.BlockService;
import software.decibel.services.user.UserService;

@RequiredArgsConstructor
@Component
public class TrackChecksUtil {

    private final BlockService blockService;
    private final TrackRepository trackRepository;

    public Track getTrackIfExistsById(Long trackId) {
        Long currentUserId = null;
        try {
            currentUserId = JwtService.getCurrentUserId();
        } catch (Exception e) {

        }
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));

        if (blockService.isBlockRelationshipActive(currentUserId, track.getUploader().getId())) {
            throw new ResourceNotFoundException("Track with id " + trackId + " not found");
        }
        checkTrackVisibility(track, currentUserId);
        return track;
    }

    private void checkTrackVisibility(Track track, Long currentUserId) {
        if (track.getVisibility() == Visibility.PRIVATE) {
            if (currentUserId == null || !track.getUploader().getId().equals(currentUserId)) {
                throw new ResourceNotFoundException("Track with id " + track.getId() + " not found");
            }
        }
    }

}
