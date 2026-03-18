package software.decibel.services;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.dtos.track.*;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.FileType;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.utils.AudioUtility;
import software.decibel.utils.FileUtilityAzure;
import software.decibel.utils.WaveFormUtility;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class TrackService {

  private final TrackRepository trackRepository;
  private final UserRepository userRepository;
  

  private final FileUtilityAzure fileUtilityAzure;
  private final WaveFormUtility waveFormUtility;
  private final AudioUtility audioUtility;
  private final TrackMapper trackMapper;

  private final ObjectMapper objectMapper;

  private final TagService tagService;

  // Returns track's status
  public TrackStatusResponse getTrackStatus(Long trackId) {

    return trackMapper.toTrackStatusResponse(getTrackById(trackId));
  }

  @Transactional
  public void deleteTrack(Long trackId) {
    Track track = getTrackById(trackId);

    deleteTrackCover(trackId);
    deleteTrackAudio(trackId);
    deleteTrackWaveformData(trackId);

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

      // Convert waveform data from json string to list of floats
      List<Float> waveformData =
          objectMapper.readValue(request.waveformData(), new TypeReference<List<Float>>() {});
      String waveformUrl = waveFormUtility.saveWaveformToAzure(waveformData, request.title());

      // Set urls manually
      createdTrack.setTrackUrl(trackUrl);
      createdTrack.setCoverUrl(coverUrl);
      createdTrack.setWaveformUrl(waveformUrl);

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
    track.setState(TrackState.UPLOADING);
    return trackRepository.save(track);
  }

  // Function to update track entity's state and save
  @Transactional
  public void updateTrackState(Track t, TrackState state) {

    t.setState(state);
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
  @Transactional
  public void deleteTrackCover(Long trackId) {
    Track track = getTrackById(trackId);

    if (track.getCoverUrl() != null) {
      fileUtilityAzure.deleteFileByUrl(track.getCoverUrl());
      track.setCoverUrl(null);
      trackRepository.save(track);
    }
  }

  // Deletes track audio from azure & sets trackUrl = null

  @Transactional
  public void deleteTrackAudio(Long trackId) {
    Track track = getTrackById(trackId);
    if (track.getTrackUrl() != null) {
      fileUtilityAzure.deleteFileByUrl(track.getTrackUrl());
      track.setTrackUrl(null);
      trackRepository.save(track);
    }
  }

  @Transactional
  public void deleteTrackWaveformData(Long trackId) {
    Track track = getTrackById(trackId);
    if (track.getWaveformUrl() != null) {
      fileUtilityAzure.deleteFileByUrl(track.getWaveformUrl());
      track.setWaveformUrl(null);
      trackRepository.save(track);
    }
  }

  // Adds tags to tracks (whether tags already exist or create ones) - tags will be title case
  @Transactional
  public Track addTrackTags(Track track, List<String> tagTitles) {
    List<Tag> tags =
        tagTitles.stream().map(tagService::getOrCreateTag).collect(Collectors.toList());

    track.setTags(tags);
    return trackRepository.save(track);
  }

  @Transactional
  public TrackPatchResponse updateTrack(Long trackId, TrackPatchRequest request) {
    Track track = getTrackById(trackId);

    if (request.title() != null) track.setTitle(request.title());
    if (request.genre() != null) track.setGenre(request.genre());
    if (request.description() != null) track.setDescription(request.description());
    if (request.releaseDate() != null) track.setReleaseDate(request.releaseDate());
    if (request.isPrivate() != null)
      track.setVisibility(request.isPrivate() ? Visibility.PRIVATE : Visibility.PUBLIC);

    if (request.coverImage() != null && !request.coverImage().isEmpty()) {
      deleteTrackCover(trackId);
      String newCoverUrl = fileUtilityAzure.saveFile(request.coverImage(), FileType.TRACK_COVERS);
      track.setCoverUrl(newCoverUrl);
    }
    if (request.tags() != null) addTrackTags(track, request.tags());

    return trackMapper.toTrackPatchResponse(trackRepository.save(track));
  }
}
