package software.decibel.services.subscription;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stripe.exception.StripeException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.subscription.CancelSubscriptionResponse;
import software.decibel.dtos.subscription.CheckoutResponse;
import software.decibel.dtos.subscription.RenewSubscriptionResponse;
import software.decibel.dtos.subscription.SubscriptionStatusResponse;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Subscription;
import software.decibel.entities.User;
import software.decibel.enums.SubscriptionStatus;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.SubscriptionNotReadyException;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.SubscriptionRepository;
import software.decibel.services.user.UserService;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final StripeService stripeService;
    private final AuthIdentityRepository authIdentityRepository;
    private final UserService userService;

    // POST /subscription/checkout
    @Transactional
    public CheckoutResponse createCheckoutSession(Long userId) {
        User user = findUser(userId);

        // Block if already has active non-cancelling subscription
        subscriptionRepository.findByUserId(userId).ifPresent(sub -> {
            if (sub.getStatus() == SubscriptionStatus.ACTIVE && !sub.isCancelAtPeriodEnd()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "User already has an active subscription");
            }
        });

        try {
            // Reuse existing Stripe customer ID if available, otherwise create new
            String customerId = subscriptionRepository.findByUserId(userId)
                    .map(Subscription::getStripeCustomerId)
                    .orElseGet(() -> {
                        try {
                            return stripeService.createCustomer(
                                    resolveEmail(user), user.getUsername());
                        } catch (StripeException e) {
                            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                    "Failed to create Stripe customer");
                        }
                    });

            // Get hosted checkout URL from Stripe
            String checkoutUrl = stripeService.createCheckoutSession(customerId);

            // Save subscription in TRIALING state — stripeSubscriptionId is NULL here
            // It will be set by the webhook once payment is confirmed
            Subscription subscription = subscriptionRepository.findByUserId(userId)
                    .orElse(Subscription.builder()
                            .user(user)
                            .stripeCustomerId(customerId)
                            .build());

            subscription.setStatus(SubscriptionStatus.TRIALING);
            subscription.setPlan("pro");
            // Note: stripeSubscriptionId and currentPeriodEnd intentionally left null
            // They are populated by activateSubscription() when webhook fires
            subscriptionRepository.save(subscription);

            return new CheckoutResponse(checkoutUrl);

        } catch (StripeException e) {
            log.error("[STRIPE] Error creating checkout session for userId={}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Payment service unavailable");
        }
    }

    // POST /subscription/cancel
    @Transactional
    public CancelSubscriptionResponse cancelSubscription(Long userId) {
        Subscription subscription = findActiveSubscription(userId);

        //stripeSubscriptionId can be null if webhook hasn't fired yet
        if (subscription.getStripeSubscriptionId() == null) {
            throw new SubscriptionNotReadyException(
                    "Subscription is not fully activated yet. Please wait for payment confirmation.");
        }

        try {
            stripeService.cancelSubscription(subscription.getStripeSubscriptionId());
            subscription.setCancelAtPeriodEnd(true);
            subscriptionRepository.save(subscription);

            //subscription.getUser().setTier(AccountTier.FREE); for immediate cancelation    
            return new CancelSubscriptionResponse("Subscription cancelled successfully", true);

        } catch (StripeException e) {
            log.error("[STRIPE] Error cancelling subscription {}: {}",
                    subscription.getStripeSubscriptionId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to cancel subscription with Stripe");
        }
    }

    // GET /subscription/status
    public SubscriptionStatusResponse getSubscriptionStatus(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                "No subscription found for user " + userId));

        return new SubscriptionStatusResponse(
                subscription.getStatus().name().toLowerCase(),
                subscription.getPlan(),
                subscription.getCurrentPeriodEnd(), // may be null if webhook hasn't fired
                subscription.isCancelAtPeriodEnd()
        );
    }

    // POST /subscription/renew
    @Transactional
    public RenewSubscriptionResponse renewSubscription(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                "No subscription found for user " + userId));

        if (!subscription.isCancelAtPeriodEnd()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Subscription is not scheduled for cancellation");
        }

        // Guard — stripeSubscriptionId can be null if webhook hasn't fired yet
        if (subscription.getStripeSubscriptionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Subscription is not fully activated yet.");
        }

        try {
            stripeService.renewSubscription(subscription.getStripeSubscriptionId());
            subscription.setCancelAtPeriodEnd(false);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);

            return new RenewSubscriptionResponse("Subscription renewed successfully", false, "active");

        } catch (StripeException e) {
            log.error("[STRIPE] Error renewing subscription {}: {}",
                    subscription.getStripeSubscriptionId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to renew subscription with Stripe");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Subscription findActiveSubscription(Long userId) {
        Subscription subscription = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                "No subscription found for user " + userId));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                && subscription.getStatus() != SubscriptionStatus.TRIALING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No active subscription to cancel");
        }

        return subscription;
    }

    private User findUser(Long userId) {
        return userService.getUserIfExistsById(userId);
    }

    private String resolveEmail(User user) {
        return authIdentityRepository.findFirstByUserId(user.getId())
                .map(AuthIdentity::getEmail)
                .orElseGet(() -> user.getUsername() + "@decibel.mock");
    }
}
