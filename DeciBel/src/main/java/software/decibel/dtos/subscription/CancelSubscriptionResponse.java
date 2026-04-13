package software.decibel.dtos.subscription;

public record CancelSubscriptionResponse(
        String message,
        boolean cancelAtPeriodEnd) {

}
