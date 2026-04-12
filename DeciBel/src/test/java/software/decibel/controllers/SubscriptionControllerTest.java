package software.decibel.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import software.decibel.dtos.subscription.CancelSubscriptionResponse;
import software.decibel.dtos.subscription.CheckoutResponse;
import software.decibel.dtos.subscription.RenewSubscriptionResponse;
import software.decibel.dtos.subscription.SubscriptionStatusResponse;
import software.decibel.services.subscription.SubscriptionService;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SubscriptionController(subscriptionService))
                .build();
    }

    @Test
    void createCheckoutSession_returnsCheckoutUrl() throws Exception {
        // FIXED: Using any() instead of anyLong() to allow nulls from the unauthenticated MockMvc context
        when(subscriptionService.createCheckoutSession(any()))
                .thenReturn(new CheckoutResponse("https://checkout.stripe.com/pay/cs_mock_abc"));

        // FIXED: Removed the rogue "/s/" typo from the URL that caused the 404
        mockMvc.perform(post("/subscription/checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl")
                        .value("https://checkout.stripe.com/pay/cs_mock_abc"));

        verify(subscriptionService).createCheckoutSession(any());
    }

    @Test
    void cancelSubscription_returnsCancellationResponse() throws Exception {
        // FIXED: Changed anyLong() to any()
        when(subscriptionService.cancelSubscription(any()))
                .thenReturn(new CancelSubscriptionResponse(
                        "Subscription cancelled successfully", true));

        mockMvc.perform(post("/subscription/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Subscription cancelled successfully"))
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));
    }

    @Test
    void getSubscriptionStatus_returnsStatus() throws Exception {
        // FIXED: Changed anyLong() to any()
        when(subscriptionService.getSubscriptionStatus(any()))
                .thenReturn(new SubscriptionStatusResponse(
                        "active", "pro", 1748736000L, false));

        mockMvc.perform(get("/subscription/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.plan").value("pro"))
                .andExpect(jsonPath("$.currentPeriodEnd").value(1748736000L))
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(false));
    }

    @Test
    void renewSubscription_returnsRenewalResponse() throws Exception {
        // FIXED: Changed anyLong() to any()
        when(subscriptionService.renewSubscription(any()))
                .thenReturn(new RenewSubscriptionResponse(
                        "Subscription renewed successfully", false, "active"));

        mockMvc.perform(post("/subscription/renew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Subscription renewed successfully"))
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(false))
                .andExpect(jsonPath("$.status").value("active"));
    }
}
