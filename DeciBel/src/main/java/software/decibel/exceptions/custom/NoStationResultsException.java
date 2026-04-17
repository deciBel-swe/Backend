package software.decibel.exceptions.custom;

public class NoStationResultsException extends RuntimeException {
  public NoStationResultsException() {
    super("No tracks found for this station.");
  }
}
