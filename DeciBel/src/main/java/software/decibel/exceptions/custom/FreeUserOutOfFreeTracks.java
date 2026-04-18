package software.decibel.exceptions.custom;

public class FreeUserOutOfFreeTracks extends RuntimeException {
  public FreeUserOutOfFreeTracks(Long userId) {
    super("User with ID '" + userId + "' is out of free tracks. Can only upload/patch to BLOCKED.");
  }
}
