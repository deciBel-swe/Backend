package software.decibel.exceptions.custom;

// Use when: trying to create a resource that already exists
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}