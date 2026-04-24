package software.decibel.services;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.stripe.exception.StripeException;

import software.decibel.dtos.subscription.CancelSubscriptionResponse;
import software.decibel.dtos.subscription.RenewSubscriptionResponse; // FIXED: Added missing import
import software.decibel.dtos.subscription.SubscriptionStatusResponse;
import software.decibel.entities.Subscription;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.SubscriptionStatus;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.SubscriptionRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.subscription.StripeService;
import software.decibel.services.subscription.SubscriptionService;
import software.decibel.services.user.UserService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // FIXED: Prevents strict stubbing crashes
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StripeService stripeService;
    @Mock
    private AuthIdentityRepository authIdentityRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void createCheckoutSession_whenUserAlreadyHasActiveSubscription_throwsConflict() {
        User user = freeUser();
        Subscription existing = activeSubscription(user);

        when(userService.getUserIfExistsById(1L)).thenReturn(user);
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.createCheckoutSession(1L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void createCheckoutSession_whenUserNotFound_throwsNotFoundException() {
        when(userService.getUserIfExistsById(99L)).thenThrow(new ResourceNotFoundException("User with id 99 not found"));

        assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.createCheckoutSession(99L));

        verify(subscriptionRepository, never()).save(any());
    }

    // ── cancelSubscription ────────────────────────────────────────────────────
    @Test
    void cancelSubscription_whenActiveSubscriptionExists_setsCancelAtPeriodEnd() throws StripeException {
        User user = proUser();
        Subscription subscription = activeSubscription(user);

        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CancelSubscriptionResponse response = subscriptionService.cancelSubscription(1L);

        assertEquals("Subscription cancelled successfully", response.message());
        assertTrue(response.cancelAtPeriodEnd());
        assertTrue(subscription.isCancelAtPeriodEnd());
        verify(stripeService).cancelSubscription(subscription.getStripeSubscriptionId());
    }

    @Test
    void cancelSubscription_whenNoSubscriptionFound_throwsNotFoundException() throws StripeException {
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.cancelSubscription(1L));

        verify(stripeService, never()).cancelSubscription(any());
    }

    @Test
    void cancelSubscription_whenSubscriptionNotActive_throwsBadRequest() throws StripeException {
        User user = proUser();
        Subscription subscription = activeSubscription(user);
        subscription.setStatus(SubscriptionStatus.CANCELLED);

        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(subscription));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.cancelSubscription(1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(stripeService, never()).cancelSubscription(any());
    }

    // ── getSubscriptionStatus ─────────────────────────────────────────────────
    @Test
    void getSubscriptionStatus_whenSubscriptionExists_returnsStatus() {
        User user = proUser();
        Subscription subscription = activeSubscription(user);

        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(subscription));

        SubscriptionStatusResponse response = subscriptionService.getSubscriptionStatus(1L);

        assertEquals("active", response.status());
        assertEquals("pro", response.plan());
        assertEquals(1748736000L, response.currentPeriodEnd());
        assertFalse(response.cancelAtPeriodEnd());
    }

    @Test
    void getSubscriptionStatus_whenNoSubscription_returnsFreeTier() {
        // Arrange
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        // Act
        SubscriptionStatusResponse response = subscriptionService.getSubscriptionStatus(1L);

        // Assert
        assertEquals("active", response.status(), "Free tier should be active");
        assertEquals("free", response.plan(), "Plan should default to free");
        long ninetyNineYearsFromNow = java.time.Instant.now().plus(365 * 99, java.time.temporal.ChronoUnit.DAYS).getEpochSecond();
        assertTrue(response.currentPeriodEnd() > ninetyNineYearsFromNow, "Should return a garbage date far in the future");
        assertFalse(response.cancelAtPeriodEnd(), "Free tier doesn't cancel");
    }

    // ── renewSubscription ─────────────────────────────────────────────────────
    @Test
    void renewSubscription_whenCancelledAtPeriodEnd_reactivatesSubscription() throws StripeException {
        User user = proUser();
        Subscription subscription = activeSubscription(user);
        subscription.setCancelAtPeriodEnd(true);

        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RenewSubscriptionResponse response = subscriptionService.renewSubscription(1L);

        assertEquals("Subscription renewed successfully", response.message());
        assertFalse(response.cancelAtPeriodEnd());
        assertEquals("active", response.status());
        assertFalse(subscription.isCancelAtPeriodEnd());
        verify(stripeService).renewSubscription(subscription.getStripeSubscriptionId());
    }

    @Test
    void renewSubscription_whenNotScheduledForCancellation_throwsConflict() throws StripeException {
        User user = proUser();
        Subscription subscription = activeSubscription(user);
        subscription.setCancelAtPeriodEnd(false);

        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(subscription));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> subscriptionService.renewSubscription(1L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(stripeService, never()).renewSubscription(any());
    }

    @Test
    void renewSubscription_whenNoSubscription_throwsNotFoundException() {
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.renewSubscription(1L));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private User freeUser() {
        return User.builder().id(1L).username("freeuser").tier(AccountTier.FREE).build();
    }

    private User proUser() {
        return User.builder().id(1L).username("prouser").tier(AccountTier.PRO).build();
    }

    private Subscription activeSubscription(User user) {
        return Subscription.builder()
                .id(1L)
                .user(user)
                .stripeSubscriptionId("sub_mock_123")
                .stripeCustomerId("cus_mock_123")
                .status(SubscriptionStatus.ACTIVE)
                .plan("pro")
                .currentPeriodEnd(1748736000L)
                .cancelAtPeriodEnd(false)
                .build();
    }
}
