package software.decibel.exceptions.custom.file;

public class FileSizeTooLargeException extends RuntimeException {
  public FileSizeTooLargeException(long limit, long size, String unit) {
    // ex."File exceeds 100MB limit. File = 200MB."
    super("File exceeds" + limit + unit + " limit. File = " + size + unit + ".");
  }
}
