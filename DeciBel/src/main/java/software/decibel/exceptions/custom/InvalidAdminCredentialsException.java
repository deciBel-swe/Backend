package software.decibel.exceptions.custom;

public class InvalidAdminCredentialsException extends RuntimeException {
    public InvalidAdminCredentialsException(String message) {
        super(message);
    }
}
