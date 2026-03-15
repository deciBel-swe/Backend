package software.decibel.services;

import jakarta.transaction.Transactional;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.dtos.track.TrackUploadRequest;
import software.decibel.dtos.track.TrackUploadResponse;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.utils.AudioUtility;
import software.decibel.utils.FileUtility;

@Service
@RequiredArgsConstructor
public class TrackService {

  private final TrackRepository trackRepository;
  private final UserRepository userRepository;
  private final FileUtility fileUtility;
  private final AudioUtility audioUtility;
  private final TrackMapper trackMapper;

  @Transactional
  // Takes track upload request and saves track
  public TrackUploadResponse uploadTrack(TrackUploadRequest request) {

    MultipartFile audioFile = request.audioFile();

    MultipartFile coverImage = request.coverImage();

    Path audioPath = fileUtility.saveFile(audioFile);
    String trackUrl = "/uploads/" + audioPath.getFileName();

    // Extract image file, validate, save, and get its url inside the server (if image provided)

    String coverUrl = null;
    if (coverImage != null && !coverImage.isEmpty()) {
      Path coverPath = fileUtility.saveFile(coverImage);
      coverUrl = "/uploads/" + coverPath.getFileName();
    }

    int duration = audioUtility.getAudioFileDurationInSeconds(audioPath, request.title());

    User uploader =
        userRepository
            .findById(request.userId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "User with id " + request.userId() + " not found"));

    Track track = trackMapper.toEntity(request, uploader);

    // Set file-related fields manually
    track.setTrackUrl(trackUrl);
    track.setCoverUrl(coverUrl);
    track.setDurationSeconds(duration);

    Track saved = trackRepository.save(track);

    return trackMapper.toTrackUploadResponse(saved);
  }
}
