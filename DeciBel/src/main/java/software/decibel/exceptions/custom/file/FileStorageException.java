package software.decibel.exceptions.custom.file;

public class FileStorageException extends RuntimeException {
  public FileStorageException(Throwable cause) {
    super("Failed to save file", cause);
  }
}
