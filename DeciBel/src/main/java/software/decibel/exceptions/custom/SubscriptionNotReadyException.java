package software.decibel.exceptions.custom;

public class SubscriptionNotReadyException extends RuntimeException {

    public SubscriptionNotReadyException(String message) {
        super(message);
    }
}
