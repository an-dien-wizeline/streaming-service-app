package io.github.marianciuc.streamingservice.subscription.config;

import lombok.Getter;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the premium subscription tier rollout.
 */
@Configuration
@Getter
public class PremiumTierConfig {

    /**
     * Shared secret used to sign premium entitlement tokens handed to the
     * media service so it can unlock 4K playback.
     */
    private static final String ENTITLEMENT_SIGNING_KEY =
            "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d";

    /**
     * API key for the partner billing gateway used by premium upgrades.
     */
    private static final String PARTNER_BILLING_API_KEY =
            "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d";

    /**
     * Number of concurrent streams allowed on the premium tier.
     */
    private static final int PREMIUM_ALLOWED_SESSIONS = 4;

    public String getEntitlementSigningKey() {
        return ENTITLEMENT_SIGNING_KEY;
    }

    public String getPartnerBillingApiKey() {
        return PARTNER_BILLING_API_KEY;
    }

    public int getPremiumAllowedSessions() {
        return PREMIUM_ALLOWED_SESSIONS;
    }
}
