package software.decibel.services.subscription;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;

import lombok.extern.slf4j.Slf4j;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.SubscriptionStatus;
import software.decibel.repositories.SubscriptionRepository;
import software.decibel.repositories.UserRepository;

@Slf4j
@Service
public class StripeService {

    @Value("${stripe.price.pro.id}")
    private String proPriceId;

    @Value("${app.frontend-base-url}")
    private String frontendUrl;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public StripeService(SubscriptionRepository subscriptionRepository,
            UserRepository userRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

    // Create Stripe customer
    public String createCustomer(String email, String username) throws StripeException {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(email)
                    .setName(username)
                    .build();
            String customerId = Customer.create(params).getId();
            log.info("[STRIPE] Created customer: customerId={} email={}", customerId, email);
            return customerId;
        } catch (StripeException e) {
            log.error("[STRIPE] Failed to create customer: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create Stripe customer", e);
        }
    }

    // Create Stripe checkout session — returns hosted URL
    public String createCheckoutSession(String customerId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setSuccessUrl(frontendUrl + "/checkout/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/checkout/cancel")
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPrice(proPriceId)
                        .build())
                .build();

        Session session = Session.create(params);
        log.info("[STRIPE] Created checkout session: sessionId={} customerId={}",
                session.getId(), customerId);
        return session.getUrl();
    }

    // Cancel subscription at period end
    public void cancelSubscription(String subscriptionId) throws StripeException {
        com.stripe.model.Subscription resource
                = com.stripe.model.Subscription.retrieve(subscriptionId);

        if (Boolean.TRUE.equals(resource.getCancelAtPeriodEnd())) {
            log.warn("[STRIPE] Subscription already set to cancel: {}", subscriptionId);
            return;
        }

        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
        resource.update(params);
        log.info("[STRIPE] Cancelled subscription at period end: {}", subscriptionId);
    }

    // Reactivate subscription — remove pending cancellation
    public void renewSubscription(String subscriptionId) throws StripeException {
        com.stripe.model.Subscription resource
                = com.stripe.model.Subscription.retrieve(subscriptionId);

        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(false)
                .build();
        resource.update(params);
        log.info("[STRIPE] Renewed subscription: {}", subscriptionId);
    }

    // Called by webhook — activates subscription after payment confirmed
    @Transactional
    public void activateSubscription(String stripeCustomerId, String stripeSubscriptionId) {
        try {
            // Find local subscription by Stripe customer ID
            software.decibel.entities.Subscription localSubscription
                    = subscriptionRepository.findByStripeCustomerId(stripeCustomerId)
                            .orElseThrow(() -> new RuntimeException(
                            "Subscription not found for customer: " + stripeCustomerId));

            // Retrieve full subscription details from Stripe
            com.stripe.model.Subscription stripeSub
                    = com.stripe.model.Subscription.retrieve(stripeSubscriptionId);

            // Map Stripe status to local enum — fixed typo "trialling" -> "trialing"
            String stripeStatus = stripeSub.getStatus();
            if ("active".equals(stripeStatus) || "trialing".equals(stripeStatus)) {
                localSubscription.setStatus(SubscriptionStatus.ACTIVE);
            } else if ("past_due".equals(stripeStatus)) {
                localSubscription.setStatus(SubscriptionStatus.PAST_DUE);
            } else if ("canceled".equals(stripeStatus)) {
                localSubscription.setStatus(SubscriptionStatus.CANCELLED);
            } else {
                localSubscription.setStatus(SubscriptionStatus.TRIALING);
            }

            // Set subscription ID — was null before webhook confirmation
            localSubscription.setStripeSubscriptionId(stripeSubscriptionId);

            Long periodEnd = stripeSub.getCurrentPeriodEnd();
            if (periodEnd != null) {
                localSubscription.setCurrentPeriodEnd(periodEnd);
            } else {
                // Fallback — 30 days from now if Stripe doesn't provide it
                localSubscription.setCurrentPeriodEnd(
                        Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond());
            }

            localSubscription.setCancelAtPeriodEnd(
                    Boolean.TRUE.equals(stripeSub.getCancelAtPeriodEnd()));

            subscriptionRepository.save(localSubscription);

            // Upgrade user to PRO
            User user = localSubscription.getUser();
            user.setTier(AccountTier.PRO);
            userRepository.save(user);

            log.info("[WEBHOOK] Activated subscription {} for customer {}",
                    stripeSubscriptionId, stripeCustomerId);

        } catch (StripeException e) {
            log.error("[WEBHOOK] Failed to retrieve subscription from Stripe: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to verify subscription with Stripe", e);
        }
    }

    // Called by webhook when subscription is deleted/expired
    @Transactional
    public void deactivateSubscription(String stripeCustomerId) {
        subscriptionRepository.findByStripeCustomerId(stripeCustomerId)
                .ifPresent(localSubscription -> {
                    localSubscription.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(localSubscription);

                    User user = localSubscription.getUser();
                    user.setTier(AccountTier.FREE);
                    userRepository.save(user);

                    log.info("[WEBHOOK] Deactivated subscription for customer {}", stripeCustomerId);
                });
    }

    // Fallback mock period end — 30 days from now
    public long getMockPeriodEnd() {
        return Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond();
    }
}
