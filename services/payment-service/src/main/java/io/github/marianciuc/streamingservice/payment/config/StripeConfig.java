package io.github.marianciuc.streamingservice.payment.config;

import com.stripe.Stripe;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Stripe configuration bean that initializes the Stripe API key from environment variables.
 * This separates credential management from security configuration and prevents key exposure in logs.
 */
@Configuration
@RequiredArgsConstructor
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }
}
