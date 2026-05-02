package software.decibel.exceptions.custom;

public class AccessDeniedException extends RuntimeException {
  public AccessDeniedException(String message) {
    super(message);
  }
}
