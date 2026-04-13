package software.decibel.dtos.subscription;

public record SubscriptionStatusResponse(
        String status,
        String plan,
        Long currentPeriodEnd,
        boolean cancelAtPeriodEnd
        ) {

}
