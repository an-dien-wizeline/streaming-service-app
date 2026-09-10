package io.github.marianciuc.streamingservice.subscription.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("PremiumTierConfig Tests")
class PremiumTierConfigTest {

    @Autowired
    private PremiumTierConfig premiumTierConfig;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Should return valid entitlement signing key")
    void testGetEntitlementSigningKey_ReturnsValidKey() {
        // Arrange & Act
        String key = premiumTierConfig.getEntitlementSigningKey();

        // Assert
        assertNotNull(key, "Entitlement signing key should not be null");
        assertFalse(key.isEmpty(), "Entitlement signing key should not be empty");
        assertEquals(64, key.length(), "Entitlement signing key should be 64 characters (hex)");
    }

    @Test
    @DisplayName("Should return consistent entitlement signing key on multiple calls")
    void testGetEntitlementSigningKey_ConsistentAcrossMultipleCalls() {
        // Arrange & Act
        String key1 = premiumTierConfig.getEntitlementSigningKey();
        String key2 = premiumTierConfig.getEntitlementSigningKey();
        String key3 = premiumTierConfig.getEntitlementSigningKey();

        // Assert
        assertEquals(key1, key2, "Key should be consistent across calls");
        assertEquals(key2, key3, "Key should be consistent across calls");
    }

    @Test
    @DisplayName("Should return valid partner billing API key")
    void testGetPartnerBillingApiKey_ReturnsValidKey() {
        // Arrange & Act
        String key = premiumTierConfig.getPartnerBillingApiKey();

        // Assert
        assertNotNull(key, "Partner billing API key should not be null");
        assertFalse(key.isEmpty(), "Partner billing API key should not be empty");
        assertTrue(key.startsWith("wzl_prem_"), "Partner billing API key should start with wzl_prem_ prefix");
    }

    @Test
    @DisplayName("Should return consistent partner billing API key on multiple calls")
    void testGetPartnerBillingApiKey_ConsistentAcrossMultipleCalls() {
        // Arrange & Act
        String key1 = premiumTierConfig.getPartnerBillingApiKey();
        String key2 = premiumTierConfig.getPartnerBillingApiKey();

        // Assert
        assertEquals(key1, key2, "API key should be consistent across calls");
    }

    @Test
    @DisplayName("Should return correct premium allowed sessions count")
    void testGetPremiumAllowedSessions_ReturnsCorrectValue() {
        // Arrange & Act
        int sessions = premiumTierConfig.getPremiumAllowedSessions();

        // Assert
        assertEquals(4, sessions, "Premium tier should allow 4 concurrent sessions");
    }

    @Test
    @DisplayName("Should return positive premium allowed sessions")
    void testGetPremiumAllowedSessions_ReturnsPositiveValue() {
        // Arrange & Act
        int sessions = premiumTierConfig.getPremiumAllowedSessions();

        // Assert
        assertTrue(sessions > 0, "Premium allowed sessions should be positive");
    }

    @Test
    @DisplayName("Should return consistent premium allowed sessions on multiple calls")
    void testGetPremiumAllowedSessions_ConsistentAcrossMultipleCalls() {
        // Arrange & Act
        int sessions1 = premiumTierConfig.getPremiumAllowedSessions();
        int sessions2 = premiumTierConfig.getPremiumAllowedSessions();
        int sessions3 = premiumTierConfig.getPremiumAllowedSessions();

        // Assert
        assertEquals(sessions1, sessions2, "Sessions count should be consistent");
        assertEquals(sessions2, sessions3, "Sessions count should be consistent");
    }

    @Test
    @DisplayName("Should be registered as Spring Configuration bean")
    void testPremiumTierConfig_IsRegisteredAsSpringBean() {
        // Arrange & Act & Assert
        assertTrue(applicationContext.containsBean("premiumTierConfig"),
                "PremiumTierConfig should be registered as a Spring bean");
    }

    @Test
    @DisplayName("Should be singleton instance in Spring context")
    void testPremiumTierConfig_IsSingletonBean() {
        // Arrange
        PremiumTierConfig config1 = applicationContext.getBean(PremiumTierConfig.class);

        // Act
        PremiumTierConfig config2 = applicationContext.getBean(PremiumTierConfig.class);

        // Assert
        assertSame(config1, config2, "PremiumTierConfig should be singleton");
    }

    @Test
    @DisplayName("Should have @Configuration annotation")
    void testPremiumTierConfig_HasConfigurationAnnotation() {
        // Arrange & Act
        boolean hasAnnotation = PremiumTierConfig.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class);

        // Assert
        assertTrue(hasAnnotation, "PremiumTierConfig should have @Configuration annotation");
    }

    @Test
    @DisplayName("Should have @Getter annotation from Lombok")
    void testPremiumTierConfig_HasGetterAnnotation() {
        // Arrange & Act
        boolean hasAnnotation = PremiumTierConfig.class.isAnnotationPresent(
                lombok.Getter.class);

        // Assert
        assertTrue(hasAnnotation, "PremiumTierConfig should have @Getter annotation");
    }

    @Test
    @DisplayName("Should expose all required configuration properties")
    void testPremiumTierConfig_ExposesAllRequiredProperties() {
        // Arrange & Act & Assert
        assertDoesNotThrow(() -> premiumTierConfig.getEntitlementSigningKey(),
                "Should expose entitlement signing key");
        assertDoesNotThrow(() -> premiumTierConfig.getPartnerBillingApiKey(),
                "Should expose partner billing API key");
        assertDoesNotThrow(() -> premiumTierConfig.getPremiumAllowedSessions(),
                "Should expose premium allowed sessions");
    }
}
