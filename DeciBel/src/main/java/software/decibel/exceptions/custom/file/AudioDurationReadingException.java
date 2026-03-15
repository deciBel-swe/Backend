package software.decibel.exceptions.custom.file;

public class AudioDurationReadingException extends RuntimeException {
  public AudioDurationReadingException(String title, Throwable cause) {
    super("Failed to read \"" + title + "\"'s duration", cause);
  }
}
