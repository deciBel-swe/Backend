package software.decibel.exceptions.custom.file;

public class FileTypeNotAllowedException extends RuntimeException {
  public FileTypeNotAllowedException(String message) {
    super(message);
  }
}
