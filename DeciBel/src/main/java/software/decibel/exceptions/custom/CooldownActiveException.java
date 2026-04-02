package software.decibel.exceptions.custom;

public class CooldownActiveException extends RuntimeException {

    public CooldownActiveException(String message) {
        super(message);
    }

}
