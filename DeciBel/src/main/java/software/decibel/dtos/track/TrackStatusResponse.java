package software.decibel.dtos.track;

import software.decibel.enums.TrackState;

public record TrackStatusResponse(
    TrackState trackState,
    Long trackId,
    Integer progressPercentage,
    String stepName,
    String errorMessage) {

  public TrackStatusResponse(TrackState trackState, Long trackId) {
    this(trackState, trackId, null, null, null);
  }

  public TrackStatusResponse(TrackState trackState, Long trackId, Integer progressPercentage, String stepName) {
    this(trackState, trackId, progressPercentage, stepName, null);
  }
}
