package software.decibel.exceptions.custom;

// Use when: entity looked up by ID or field doesn't exist in the DB
public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String message) {
    super(message);
  }
}
