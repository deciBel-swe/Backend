package software.decibel.exceptions.custom;

public class TrackAlreadyPublishedException extends RuntimeException {
  public TrackAlreadyPublishedException(Long trackId) {
    super("Track with id " + trackId + " already published.");
  }
}
