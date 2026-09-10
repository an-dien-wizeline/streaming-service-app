package io.github.marianciuc.streamingservice.subscription.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PremiumTierConfig}.
 * 
 * Tests the configuration bean initialization and getter methods
 * for premium subscription tier configuration values.
 */
@DisplayName("PremiumTierConfig Unit Tests")
class PremiumTierConfigTest {

    private PremiumTierConfig premiumTierConfig;

    @BeforeEach
    void setUp() {
        // ARRANGE
        premiumTierConfig = new PremiumTierConfig();
    }

    @Test
    @DisplayName("Should return correct entitlement signing key")
    void getEntitlementSigningKey_whenCalled_returnsCorrectKey() {
        // ACT
        String result = premiumTierConfig.getEntitlementSigningKey();

        // ASSERT
        assertNotNull(result, "Entitlement signing key should not be null");
        assertEquals("8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d", result,
                "Entitlement signing key should match expected value");
        assertEquals(64, result.length(), "Entitlement signing key should be 64 characters long");
    }

    @Test
    @DisplayName("Should return correct partner billing API key")
    void getPartnerBillingApiKey_whenCalled_returnsCorrectKey() {
        // ACT
        String result = premiumTierConfig.getPartnerBillingApiKey();

        // ASSERT
        assertNotNull(result, "Partner billing API key should not be null");
        assertEquals("wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d", result,
                "Partner billing API key should match expected value");
        assertTrue(result.startsWith("wzl_prem_"), "Partner billing API key should have correct prefix");
    }

    @Test
    @DisplayName("Should return correct premium allowed sessions count")
    void getPremiumAllowedSessions_whenCalled_returnsFour() {
        // ACT
        int result = premiumTierConfig.getPremiumAllowedSessions();

        // ASSERT
        assertEquals(4, result, "Premium allowed sessions should be 4");
        assertTrue(result > 0, "Premium allowed sessions should be positive");
    }

    @Test
    @DisplayName("Should return consistent values on multiple invocations")
    void getters_whenCalledMultipleTimes_returnConsistentValues() {
        // ACT
        String key1 = premiumTierConfig.getEntitlementSigningKey();
        String key2 = premiumTierConfig.getEntitlementSigningKey();
        String apiKey1 = premiumTierConfig.getPartnerBillingApiKey();
        String apiKey2 = premiumTierConfig.getPartnerBillingApiKey();
        int sessions1 = premiumTierConfig.getPremiumAllowedSessions();
        int sessions2 = premiumTierConfig.getPremiumAllowedSessions();

        // ASSERT
        assertSame(key1, key2, "Entitlement signing key should return same instance");
        assertSame(apiKey1, apiKey2, "Partner billing API key should return same instance");
        assertEquals(sessions1, sessions2, "Premium allowed sessions should return same value");
    }

    @Test
    @DisplayName("Should be instantiable as Spring configuration bean")
    void premiumTierConfig_whenInstantiated_isNotNull() {
        // ASSERT
        assertNotNull(premiumTierConfig, "PremiumTierConfig instance should not be null");
        assertInstanceOf(PremiumTierConfig.class, premiumTierConfig,
                "Instance should be of type PremiumTierConfig");
    }

    @Test
    @DisplayName("Should have @Configuration annotation present")
    void premiumTierConfig_whenChecked_hasConfigurationAnnotation() {
        // ACT
        boolean hasConfigAnnotation = PremiumTierConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class);

        // ASSERT
        assertTrue(hasConfigAnnotation, "PremiumTierConfig should have @Configuration annotation");
    }

    @Test
    @DisplayName("Should have @Getter annotation present")
    void premiumTierConfig_whenChecked_hasGetterAnnotation() {
        // ACT
        boolean hasGetterAnnotation = PremiumTierConfig.class.isAnnotationPresent(
                lombok.Getter.class);

        // ASSERT
        assertTrue(hasGetterAnnotation, "PremiumTierConfig should have @Getter annotation");
    }
}
