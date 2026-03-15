package software.decibel.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.dtos.track.TrackUploadRequest;
import software.decibel.dtos.track.TrackUploadResponse;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.FileType;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.utils.AudioUtility;
import software.decibel.utils.FileUtilityAzure;

@Service
@RequiredArgsConstructor
public class TrackService {

  private final TrackRepository trackRepository;
  private final UserRepository userRepository;

  private final FileUtilityAzure fileUtilityAzure;
  private final AudioUtility audioUtility;
  private final TrackMapper trackMapper;

  @Transactional
  // Takes track upload request and saves track
  public TrackUploadResponse uploadTrack(TrackUploadRequest request) {

    // save audio file in azure
    MultipartFile audioFile = request.audioFile();
    String trackUrl = fileUtilityAzure.saveFile(audioFile, FileType.AUDIO);

    // Extract image file, validate, save, and get its url inside the server (if image provided)
    MultipartFile coverImage = request.coverImage();
    String coverUrl = null;
    if (coverImage != null && !coverImage.isEmpty()) {
      coverUrl = fileUtilityAzure.saveFile(coverImage, FileType.TRACK_COVERS);
    }

    // get userid and user from jwt
    Long userId = JwtService.getCurrentUserId();
    User uploader =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User with id " + userId + " not found"));

    // convert track to entity and save
    Track track = trackMapper.toEntity(request, uploader);

    // Set file-related fields manually
    track.setTrackUrl(trackUrl);
    track.setCoverUrl(coverUrl);
    track.setDurationSeconds(
        audioUtility.getAudioFileDurationInSeconds(audioFile, request.title()));

    Track saved = trackRepository.save(track);

    return trackMapper.toTrackUploadResponse(saved);
  }
}
