package software.decibel.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.subscription.CancelSubscriptionResponse;
import software.decibel.dtos.subscription.CheckoutResponse;
import software.decibel.dtos.subscription.RenewSubscriptionResponse;
import software.decibel.dtos.subscription.SubscriptionStatusResponse;
import software.decibel.services.JwtService;
import software.decibel.services.subscription.SubscriptionService;

@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // POST /subscriptions/checkout
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> createCheckoutSession() {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(subscriptionService.createCheckoutSession(currentUserId));
    }

    // POST /subscription/cancel
    @PostMapping("/cancel")
    public ResponseEntity<CancelSubscriptionResponse> cancelSubscription() {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(subscriptionService.cancelSubscription(currentUserId));
    }

    //GET /subscription/status
    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusResponse> getSubscriptionStatus() {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(subscriptionService.getSubscriptionStatus(currentUserId));
    }

    // POST /subscription/renew
    @PostMapping("/renew")
    public ResponseEntity<RenewSubscriptionResponse> renewSubscription() {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(subscriptionService.renewSubscription(currentUserId));
    }

}
