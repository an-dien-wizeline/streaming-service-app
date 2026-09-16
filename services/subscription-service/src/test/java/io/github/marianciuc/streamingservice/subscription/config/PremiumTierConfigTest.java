package io.github.marianciuc.streamingservice.subscription.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PremiumTierConfig configuration bean.
 * Tests verify that configuration values are correctly exposed through getters.
 */
public class PremiumTierConfigTest {

    // System Under Test (SUT)
    private final PremiumTierConfig sut = new PremiumTierConfig();

    /**
     * Test: getEntitlementSigningKey returns the correct signing key
     * Scenario: Configuration bean is instantiated
     * Expected: Returns the hardcoded entitlement signing key string
     */
    @Test
    public void getEntitlementSigningKey_whenConfigBeanInstantiated_returnsCorrectSigningKey() {
        // ARRANGE
        String expectedKey = "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d";

        // ACT
        String actualKey = sut.getEntitlementSigningKey();

        // ASSERT
        assertNotNull(actualKey, "Entitlement signing key should not be null");
        assertEquals(expectedKey, actualKey, "Entitlement signing key should match expected value");
        assertEquals(64, actualKey.length(), "Entitlement signing key should be 64 characters (256-bit hex)");
    }

    /**
     * Test: getPartnerBillingApiKey returns the correct API key
     * Scenario: Configuration bean is instantiated
     * Expected: Returns the hardcoded partner billing API key string
     */
    @Test
    public void getPartnerBillingApiKey_whenConfigBeanInstantiated_returnsCorrectApiKey() {
        // ARRANGE
        String expectedKey = "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d";

        // ACT
        String actualKey = sut.getPartnerBillingApiKey();

        // ASSERT
        assertNotNull(actualKey, "Partner billing API key should not be null");
        assertEquals(expectedKey, actualKey, "Partner billing API key should match expected value");
        assertTrue(actualKey.startsWith("wzl_prem_"), "Partner billing API key should have correct prefix");
    }

    /**
     * Test: getPremiumAllowedSessions returns the correct session count
     * Scenario: Configuration bean is instantiated
     * Expected: Returns 4 as the allowed concurrent sessions
     */
    @Test
    public void getPremiumAllowedSessions_whenConfigBeanInstantiated_returnsFourSessions() {
        // ARRANGE
        int expectedSessions = 4;

        // ACT
        int actualSessions = sut.getPremiumAllowedSessions();

        // ASSERT
        assertEquals(expectedSessions, actualSessions, "Premium allowed sessions should be 4");
        assertTrue(actualSessions > 0, "Premium allowed sessions should be positive");
    }

    /**
     * Test: Multiple calls to getters return consistent values (immutability check)
     * Scenario: Getters are called multiple times
     * Expected: Same values are returned on each call
     */
    @Test
    public void getters_whenCalledMultipleTimes_returnConsistentValues() {
        // ACT
        String key1 = sut.getEntitlementSigningKey();
        String key2 = sut.getEntitlementSigningKey();
        String apiKey1 = sut.getPartnerBillingApiKey();
        String apiKey2 = sut.getPartnerBillingApiKey();
        int sessions1 = sut.getPremiumAllowedSessions();
        int sessions2 = sut.getPremiumAllowedSessions();

        // ASSERT
        assertEquals(key1, key2, "Entitlement signing key should be consistent across calls");
        assertEquals(apiKey1, apiKey2, "Partner billing API key should be consistent across calls");
        assertEquals(sessions1, sessions2, "Premium allowed sessions should be consistent across calls");
    }

    /**
     * Test: Configuration bean can be instantiated
     * Scenario: New instance is created
     * Expected: Bean is created without exceptions
     */
    @Test
    public void constructor_whenCalled_createsConfigBeanSuccessfully() {
        // ACT & ASSERT
        assertDoesNotThrow(() -> {
            PremiumTierConfig config = new PremiumTierConfig();
            assertNotNull(config, "Config bean should be instantiated");
        });
    }

    /**
     * Test: All configuration values are non-empty
     * Scenario: Configuration bean is queried for all values
     * Expected: No empty or blank strings are returned
     */
    @Test
    public void allConfigValues_whenRetrieved_areNonEmpty() {
        // ACT
        String signingKey = sut.getEntitlementSigningKey();
        String apiKey = sut.getPartnerBillingApiKey();
        int sessions = sut.getPremiumAllowedSessions();

        // ASSERT
        assertFalse(signingKey.isEmpty(), "Entitlement signing key should not be empty");
        assertFalse(signingKey.isBlank(), "Entitlement signing key should not be blank");
        assertFalse(apiKey.isEmpty(), "Partner billing API key should not be empty");
        assertFalse(apiKey.isBlank(), "Partner billing API key should not be blank");
        assertTrue(sessions > 0, "Premium allowed sessions should be greater than 0");
    }
}
