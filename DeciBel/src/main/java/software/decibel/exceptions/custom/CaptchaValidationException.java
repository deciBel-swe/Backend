package software.decibel.exceptions.custom;

public class CaptchaValidationException extends RuntimeException {

    public CaptchaValidationException(String message) {
        super(message);
    }
}
