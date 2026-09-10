package io.github.marianciuc.streamingservice.subscription.controller;

import io.github.marianciuc.streamingservice.subscription.service.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionController - Grant Premium Tier Tests")
class SubscriptionControllerGrantPremiumTierTest {

    @Mock
    private UserSubscriptionService userSubscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    private MockMvc mockMvc;
    private UUID testUserId;
    private UUID testTierId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(subscriptionController).build();
        testUserId = UUID.randomUUID();
        testTierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should grant premium tier with valid userId and tierId")
    void testGrantPremiumTier_WithValidParameters_ReturnsOk() throws Exception {
        // Arrange
        doNothing().when(userSubscriptionService).grantPremiumTier(testUserId, testTierId);

        // Act & Assert
        mockMvc.perform(post("/api/subscriptions/premium/grant")
                .param("userId", testUserId.toString())
                .param("tierId", testTierId.toString()))
                .andExpect(status().isOk());

        verify(userSubscriptionService, times(1)).grantPremiumTier(testUserId, testTierId);
    }

    @Test
    @DisplayName("Should return 200 OK when premium tier granted successfully")
    void testGrantPremiumTier_ReturnsOkStatus() throws Exception {
        // Arrange
        doNothing().when(userSubscriptionService).grantPremiumTier(testUserId, testTierId);

        // Act
        ResponseEntity<Void> response = subscriptionController.grantPremiumTier(testUserId, testTierId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Should return empty response body")
    void testGrantPremiumTier_ReturnsEmptyBody() throws Exception {
        // Arrange
        doNothing().when(userSubscriptionService).grantPremiumTier(testUserId, testTierId);

        // Act
        ResponseEntity<Void> response = subscriptionController.grantPremiumTier(testUserId, testTierId);

        // Assert
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("Should call service exactly once with correct parameters")
    void testGrantPremiumTier_CallsServiceWithCorrectParameters() {
        // Arrange
        doNothing().when(userSubscriptionService).grantPremiumTier(testUserId, testTierId);

        // Act
        subscriptionController.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(userSubscriptionService, times(1)).grantPremiumTier(testUserId, testTierId);
        verifyNoMoreInteractions(userSubscriptionService);
    }

    @Test
    @DisplayName("Should pass userId parameter to service")
    void testGrantPremiumTier_PassesUserIdToService() {
        // Arrange
        UUID expectedUserId = UUID.randomUUID();
        doNothing().when(userSubscriptionService).grantPremiumTier(expectedUserId, testTierId);

        // Act
        subscriptionController.grantPremiumTier(expectedUserId, testTierId);

        // Assert
        verify(userSubscriptionService).grantPremiumTier(expectedUserId, testTierId);
    }

    @Test
    @DisplayName("Should pass tierId parameter to service")
    void testGrantPremiumTier_PassesTierIdToService() {
        // Arrange
        UUID expectedTierId = UUID.randomUUID();
        doNothing().when(userSubscriptionService).grantPremiumTier(testUserId, expectedTierId);

        // Act
        subscriptionController.grantPremiumTier(testUserId, expectedTierId);

        // Assert
        verify(userSubscriptionService).grantPremiumTier(testUserId, expectedTierId);
    }

    @Test
    @DisplayName("Should handle null userId gracefully")
    void testGrantPremiumTier_WithNullUserId_ThrowsException() {
        // Arrange & Act & Assert
        assertThrows(NullPointerException.class,
                () -> subscriptionController.grantPremiumTier(null, testTierId));
    }

    @Test
    @DisplayName("Should handle null tierId gracefully")
    void testGrantPremiumTier_WithNullTierId_ThrowsException() {
        // Arrange & Act & Assert
        assertThrows(NullPointerException.class,
                () -> subscriptionController.grantPremiumTier(testUserId, null));
    }

    @Test
    @DisplayName("Should propagate service exceptions to caller")
    void testGrantPremiumTier_WhenServiceThrowsException_PropagatesException() {
        // Arrange
        doThrow(new IllegalArgumentException("Invalid tier"))
                .when(userSubscriptionService).grantPremiumTier(testUserId, testTierId);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> subscriptionController.grantPremiumTier(testUserId, testTierId));
    }

    @Test
    @DisplayName("Should handle RuntimeException from service")
    void testGrantPremiumTier_WhenServiceThrowsRuntimeException_PropagatesException() {
        // Arrange
        doThrow(new RuntimeException("Service error"))
                .when(userSubscriptionService).grantPremiumTier(testUserId, testTierId);

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> subscriptionController.grantPremiumTier(testUserId, testTierId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000-0000-0000-0000-000000000000", "12345678-1234-1234-1234-123456789012"})
    @DisplayName("Should handle various valid UUID formats")
    void testGrantPremiumTier_WithVariousValidUUIDs(String uuidString) {
        // Arrange
        UUID uuid = UUID.fromString(uuidString);
        doNothing().when(userSubscriptionService).grantPremiumTier(uuid, testTierId);

        // Act & Assert
        assertDoesNotThrow(() -> subscriptionController.grantPremiumTier(uuid, testTierId));
    }

    @Test
    @DisplayName("Should be idempotent - multiple calls with same parameters")
    void testGrantPremiumTier_IsIdempotent() {
        // Arrange
        doNothing().when(userSubscriptionService).grantPremiumTier(testUserId, testTierId);

        // Act
        subscriptionController.grantPremiumTier(testUserId, testTierId);
        subscriptionController.grantPremiumTier(testUserId, testTierId);
        subscriptionController.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(userSubscriptionService, times(3)).grantPremiumTier(testUserId, testTierId);
    }

    @Test
    @DisplayName("Should have @PostMapping annotation")
    void testGrantPremiumTier_HasPostMappingAnnotation() throws NoSuchMethodException {
        // Arrange & Act
        var method = SubscriptionController.class.getMethod("grantPremiumTier", UUID.class, UUID.class);
        boolean hasAnnotation = method.isAnnotationPresent(
                org.springframework.web.bind.annotation.PostMapping.class);

        // Assert
        assertTrue(hasAnnotation, "grantPremiumTier should have @PostMapping annotation");
    }

    @Test
    @DisplayName("Should map to /premium/grant endpoint")
    void testGrantPremiumTier_MapsToCorrectEndpoint() throws NoSuchMethodException {
        // Arrange & Act
        var method = SubscriptionController.class.getMethod("grantPremiumTier", UUID.class, UUID.class);
        var annotation = method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);

        // Assert
        assertNotNull(annotation);
        assertEquals(1, annotation.value().length);
        assertEquals("/premium/grant", annotation.value()[0]);
    }
}
