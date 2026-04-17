package software.decibel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.stripe.Stripe;

import jakarta.annotation.PostConstruct;

@Configuration
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @PostConstruct
    public void initStripe() {
        // takes the key from application.properties 
        // and gives it to the Stripe library
        Stripe.apiKey = stripeApiKey;
    }
}
