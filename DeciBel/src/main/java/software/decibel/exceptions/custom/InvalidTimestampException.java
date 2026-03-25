package software.decibel.exceptions.custom;

public class InvalidTimestampException extends RuntimeException {
  public InvalidTimestampException(Integer trackDuration, Integer timestamp) {
    super("Timestamp " + timestamp + "s exceeds track duration of " + trackDuration + "s");
  }
}
