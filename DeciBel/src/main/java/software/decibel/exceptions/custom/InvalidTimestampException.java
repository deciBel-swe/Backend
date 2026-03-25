package software.decibel.exceptions.custom;

// Called when a comment's timestamp (the time of a track that's being commented on ) is greater
// than the comment's duration (should be impossible)
public class InvalidTimestampException extends RuntimeException {
  public InvalidTimestampException(Integer trackDuration, Integer timestamp) {
    super("Timestamp " + timestamp + "s exceeds track duration of " + trackDuration + "s");
  }
}
