package software.decibel.exceptions.custom;

public class UnauthorizedActionException extends RuntimeException {
  public UnauthorizedActionException(String message) {
    super(message);
  }
}
