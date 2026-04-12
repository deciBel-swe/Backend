package software.decibel.dtos.subscription;

public record RenewSubscriptionResponse(
        String message,
        boolean cancelAtPeriodEnd,
        String status
        ) {

}
