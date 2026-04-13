package software.decibel.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

import lombok.extern.slf4j.Slf4j;
import software.decibel.services.subscription.StripeService;

@Slf4j
@RestController
@RequestMapping("/webhook")
public class StripeWebhookController {

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    private final StripeService stripeService;

    public StripeWebhookController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        //Verify webhook signature
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            log.warn("[WEBHOOK] Invalid Stripe signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("[WEBHOOK] Failed to parse Stripe event: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook parse error");
        }

        //Bypass Stripe's Java models Read the raw JSON directly.
        JsonObject jsonObject = JsonParser.parseString(payload).getAsJsonObject();
        JsonObject dataObject = jsonObject.getAsJsonObject("data").getAsJsonObject("object");

        // Extract Customer ID
        String customerId = null;
        if (dataObject.has("customer") && !dataObject.get("customer").isJsonNull()) {
            // For most events, customer is a direct field
            customerId = dataObject.get("customer").getAsString();
        }

        //Extract Subscription ID (Handling Stripe's hidden locations)
        String subscriptionId = null;
        if (dataObject.has("subscription") && !dataObject.get("subscription").isJsonNull()) {
            // The standard location
            subscriptionId = dataObject.get("subscription").getAsString();
        } else if (dataObject.has("parent") && !dataObject.get("parent").isJsonNull()) {
            //location inside invoice items
            JsonObject parent = dataObject.getAsJsonObject("parent");
            if (parent.has("subscription_details") && !parent.get("subscription_details").isJsonNull()) {
                subscriptionId = parent.getAsJsonObject("subscription_details").get("subscription").getAsString();
            }
        } else if (dataObject.has("id") && !dataObject.get("id").isJsonNull() && dataObject.get("id").getAsString().startsWith("sub_")) {
            subscriptionId = dataObject.get("id").getAsString();
        }

        //Handle events using the safely extracted strings
        try {
            switch (event.getType()) {

                case "checkout.session.completed" -> {
                    log.info("[WEBHOOK] checkout.session.completed: customer={} subscription={}", customerId, subscriptionId);
                    if (customerId != null && subscriptionId != null) {
                        stripeService.activateSubscription(customerId, subscriptionId);
                    }
                }

                case "invoice.payment_succeeded" -> {
                    log.info("[WEBHOOK] invoice.payment_succeeded: customer={} subscription={}", customerId, subscriptionId);
                    if (customerId != null && subscriptionId != null) {
                        stripeService.activateSubscription(customerId, subscriptionId);
                    }
                }

                case "customer.subscription.deleted" -> {
                    log.info("[WEBHOOK] customer.subscription.deleted: subscription={} customer={}", subscriptionId, customerId);
                    if (customerId != null) {
                        stripeService.deactivateSubscription(customerId);
                    }
                }

                default ->
                    log.info("[WEBHOOK] Unhandled event type={}", event.getType());
            }
        } catch (Exception e) {
            log.error("[WEBHOOK] Error handling event type={}: {}", event.getType(), e.getMessage(), e);
            // Return 200 OK even on error so Stripe stops retrying the same broken payload
            return ResponseEntity.ok("Handled with internal errors");
        }

        return ResponseEntity.ok("Success");
    }
}
