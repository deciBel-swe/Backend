package software.decibel.exceptions.custom;

public class AzureFileStorageException extends RuntimeException {
  public AzureFileStorageException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
