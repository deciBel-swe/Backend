package software.decibel.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.dtos.track.TrackStatusResponse;
import software.decibel.dtos.track.TrackUploadRequest;
import software.decibel.dtos.track.TrackUploadResponse;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.FileType;
import software.decibel.enums.TrackState;
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

  private final TagService tagService;

  // Returns track's status
  public TrackStatusResponse getTrackStatus(Long trackId) {

    return trackMapper.toTrackStatusResponse(getTrackById(trackId));
  }

  public void deleteTrack(Long trackId) {
    Track track = getTrackById(trackId);

    deleteTrackCover(trackId);
    deleteTrackAudio(trackId);
    // TODO: DELETE WAVEFORM FILE IN AZURE AFTER IMPLEMENTING WAVEFORM_URL

    trackRepository.delete(track);
  }

  // Takes track upload request and saves track
  // Not transactional as track's insertions an updates must survive to reflect track states
  public TrackUploadResponse uploadTrack(TrackUploadRequest request) {

    // get userid and user from jwt
    Long userId = JwtService.getCurrentUserId();
    User uploader =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ResourceNotFoundException("User with id " + userId + " not found"));

    // convert track to entity and save as UPLOADING
    Track track = trackMapper.toEntity(request, uploader);
    Track createdTrack = createUploadingTrack(track);

    // Uploading audio/image files & Processing the audio file for duration may cause exceptions to
    // be handled
    try {
      // save audio file in azure and get its url inside the server
      MultipartFile audioFile = request.audioFile();
      String trackUrl = fileUtilityAzure.saveFile(audioFile, FileType.AUDIO);

      // Extract image file, save, and get its url inside the server (if image provided)
      MultipartFile coverImage = request.coverImage();
      String coverUrl = null;
      if (coverImage != null && !coverImage.isEmpty()) {
        coverUrl = fileUtilityAzure.saveFile(coverImage, FileType.TRACK_COVERS);
      }

      // Set urls manually
      createdTrack.setTrackUrl(trackUrl);
      createdTrack.setCoverUrl(coverUrl);

      // save track as PROCESSING
      updateTrackState(createdTrack, TrackState.PROCESSING);

      createdTrack.setDurationSeconds(
          audioUtility.getAudioFileDurationInSeconds(audioFile, request.title()));

      // after processing (getting duration is done) save track as FINISHED
      updateTrackState(createdTrack, TrackState.FINISHED);
      Track saved = trackRepository.save(createdTrack);

      return trackMapper.toTrackUploadResponse(saved);

    } catch (Exception e) {
      updateTrackState(track, TrackState.FAILED);
      throw e;
    }
  }

  // ------------- TRACK SERVICE HELPER FUNCTIONS ---------------------
  // Function to save track entity & set state = uploading
  @Transactional
  public Track createUploadingTrack(Track track) {
    track.setTrackState(TrackState.UPLOADING);
    return trackRepository.save(track);
  }

  // Function to update track entity's state and save
  @Transactional
  public void updateTrackState(Track t, TrackState state) {

    t.setTrackState(state);
    trackRepository.save(t);
  }

  // Returns track entity by id and throws exception if not found
  public Track getTrackById(Long trackId) {
    return trackRepository
        .findById(trackId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
  }

  // Deletes track cover from azure & sets coverUrl = null
  public void deleteTrackCover(Long trackId) {
    Track track = getTrackById(trackId);

    if (track.getCoverUrl() != null) {
      fileUtilityAzure.deleteFileByUrl(track.getCoverUrl());
      track.setCoverUrl(null);
      trackRepository.save(track);
    }
  }

  // Deletes track audio from azure & sets trackUrl = null

  public void deleteTrackAudio(Long trackId) {
    Track track = getTrackById(trackId);
    if (track.getTrackUrl() != null) {
      fileUtilityAzure.deleteFileByUrl(track.getTrackUrl());
      track.setTrackUrl(null);
      trackRepository.save(track);
    }
  }
}
