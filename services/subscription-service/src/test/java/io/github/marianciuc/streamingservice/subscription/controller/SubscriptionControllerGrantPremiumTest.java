package io.github.marianciuc.streamingservice.subscription.controller;

import io.github.marianciuc.streamingservice.subscription.exceptions.NotFoundException;
import io.github.marianciuc.streamingservice.subscription.service.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SubscriptionController#grantPremiumTier(UUID, UUID)}.
 * 
 * Tests the REST endpoint for granting premium subscription tiers including:
 * - Successful grant operations with valid parameters
 * - Service method invocation verification
 * - Response status and body validation
 * - Error handling and exception propagation
 * - Parameter binding and validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionController.grantPremiumTier Unit Tests")
class SubscriptionControllerGrantPremiumTest {

    @Mock
    private UserSubscriptionService userSubscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    private UUID userId;
    private UUID tierId;

    @BeforeEach
    void setUp() {
        // ARRANGE - Common test data
        userId = UUID.randomUUID();
        tierId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should return 200 OK when granting premium tier with valid parameters")
    void grantPremiumTier_whenValidParameters_returns200OK() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        ResponseEntity<Void> response = subscriptionController.grantPremiumTier(userId, tierId);

        // ASSERT
        assertNotNull(response, "Response should not be null");
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Response status should be 200 OK");
        assertNull(response.getBody(), "Response body should be null for Void type");
    }

    @Test
    @DisplayName("Should call userSubscriptionService.grantPremiumTier with correct parameters")
    void grantPremiumTier_whenCalled_invokesServiceWithCorrectParameters() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        subscriptionController.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(userSubscriptionService, times(1)).grantPremiumTier(userId, tierId);
        verify(userSubscriptionService, times(1)).grantPremiumTier(eq(userId), eq(tierId));
    }

    @Test
    @DisplayName("Should call service method exactly once")
    void grantPremiumTier_whenCalled_invokesServiceExactlyOnce() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        subscriptionController.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(userSubscriptionService, times(1)).grantPremiumTier(any(UUID.class), any(UUID.class));
        verifyNoMoreInteractions(userSubscriptionService);
    }

    @Test
    @DisplayName("Should propagate NotFoundException when tier ID is invalid")
    void grantPremiumTier_whenTierIdIsInvalid_throwsNotFoundException() {
        // ARRANGE
        UUID invalidTierId = UUID.randomUUID();
        doThrow(new NotFoundException("Subscription tier not found"))
                .when(userSubscriptionService).grantPremiumTier(userId, invalidTierId);

        // ACT & ASSERT
        assertThrows(NotFoundException.class,
                () -> subscriptionController.grantPremiumTier(userId, invalidTierId),
                "Should propagate NotFoundException from service layer");

        verify(userSubscriptionService, times(1)).grantPremiumTier(userId, invalidTierId);
    }

    @Test
    @DisplayName("Should propagate NotFoundException when user ID is invalid")
    void grantPremiumTier_whenUserIdIsInvalid_throwsNotFoundException() {
        // ARRANGE
        UUID invalidUserId = UUID.randomUUID();
        doThrow(new NotFoundException("User not found"))
                .when(userSubscriptionService).grantPremiumTier(invalidUserId, tierId);

        // ACT & ASSERT
        assertThrows(NotFoundException.class,
                () -> subscriptionController.grantPremiumTier(invalidUserId, tierId),
                "Should propagate NotFoundException from service layer");

        verify(userSubscriptionService, times(1)).grantPremiumTier(invalidUserId, tierId);
    }

    @Test
    @DisplayName("Should handle null userId parameter")
    void grantPremiumTier_whenUserIdIsNull_throwsException() {
        // ARRANGE
        doThrow(new NullPointerException("User ID cannot be null"))
                .when(userSubscriptionService).grantPremiumTier(null, tierId);

        // ACT & ASSERT
        assertThrows(NullPointerException.class,
                () -> subscriptionController.grantPremiumTier(null, tierId),
                "Should throw exception when userId is null");

        verify(userSubscriptionService, times(1)).grantPremiumTier(null, tierId);
    }

    @Test
    @DisplayName("Should handle null tierId parameter")
    void grantPremiumTier_whenTierIdIsNull_throwsException() {
        // ARRANGE
        doThrow(new IllegalArgumentException("Tier ID cannot be null"))
                .when(userSubscriptionService).grantPremiumTier(userId, null);

        // ACT & ASSERT
        assertThrows(IllegalArgumentException.class,
                () -> subscriptionController.grantPremiumTier(userId, null),
                "Should throw exception when tierId is null");

        verify(userSubscriptionService, times(1)).grantPremiumTier(userId, null);
    }

    @Test
    @DisplayName("Should handle both parameters being null")
    void grantPremiumTier_whenBothParametersAreNull_throwsException() {
        // ARRANGE
        doThrow(new NullPointerException("Parameters cannot be null"))
                .when(userSubscriptionService).grantPremiumTier(null, null);

        // ACT & ASSERT
        assertThrows(NullPointerException.class,
                () -> subscriptionController.grantPremiumTier(null, null),
                "Should throw exception when both parameters are null");

        verify(userSubscriptionService, times(1)).grantPremiumTier(null, null);
    }

    @Test
    @DisplayName("Should return ResponseEntity with empty body")
    void grantPremiumTier_whenSuccessful_returnsEmptyBody() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        ResponseEntity<Void> response = subscriptionController.grantPremiumTier(userId, tierId);

        // ASSERT
        assertNull(response.getBody(), "Response body should be null");
        assertFalse(response.hasBody(), "Response should not have a body");
    }

    @Test
    @DisplayName("Should handle runtime exceptions from service layer")
    void grantPremiumTier_whenServiceThrowsRuntimeException_propagatesException() {
        // ARRANGE
        RuntimeException runtimeException = new RuntimeException("Unexpected error");
        doThrow(runtimeException)
                .when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT & ASSERT
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> subscriptionController.grantPremiumTier(userId, tierId),
                "Should propagate RuntimeException from service layer");

        assertEquals("Unexpected error", thrown.getMessage(),
                "Exception message should be preserved");
        verify(userSubscriptionService, times(1)).grantPremiumTier(userId, tierId);
    }

    @Test
    @DisplayName("Should handle multiple sequential calls with different parameters")
    void grantPremiumTier_whenCalledMultipleTimes_handlesEachCallIndependently() {
        // ARRANGE
        UUID userId1 = UUID.randomUUID();
        UUID tierId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID tierId2 = UUID.randomUUID();

        doNothing().when(userSubscriptionService).grantPremiumTier(any(UUID.class), any(UUID.class));

        // ACT
        ResponseEntity<Void> response1 = subscriptionController.grantPremiumTier(userId1, tierId1);
        ResponseEntity<Void> response2 = subscriptionController.grantPremiumTier(userId2, tierId2);

        // ASSERT
        assertEquals(HttpStatus.OK, response1.getStatusCode(),
                "First call should return 200 OK");
        assertEquals(HttpStatus.OK, response2.getStatusCode(),
                "Second call should return 200 OK");

        verify(userSubscriptionService, times(1)).grantPremiumTier(userId1, tierId1);
        verify(userSubscriptionService, times(1)).grantPremiumTier(userId2, tierId2);
        verify(userSubscriptionService, times(2)).grantPremiumTier(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should handle same user being granted premium tier multiple times")
    void grantPremiumTier_whenSameUserCalledMultipleTimes_invokesServiceEachTime() {
        // ARRANGE
        UUID differentTierId = UUID.randomUUID();
        doNothing().when(userSubscriptionService).grantPremiumTier(eq(userId), any(UUID.class));

        // ACT
        subscriptionController.grantPremiumTier(userId, tierId);
        subscriptionController.grantPremiumTier(userId, differentTierId);

        // ASSERT
        verify(userSubscriptionService, times(1)).grantPremiumTier(userId, tierId);
        verify(userSubscriptionService, times(1)).grantPremiumTier(userId, differentTierId);
        verify(userSubscriptionService, times(2)).grantPremiumTier(eq(userId), any(UUID.class));
    }

    @Test
    @DisplayName("Should not modify parameters before passing to service")
    void grantPremiumTier_whenCalled_passesParametersUnmodified() {
        // ARRANGE
        UUID originalUserId = UUID.fromString("12345678-1234-1234-1234-123456789012");
        UUID originalTierId = UUID.fromString("87654321-4321-4321-4321-210987654321");

        doNothing().when(userSubscriptionService).grantPremiumTier(originalUserId, originalTierId);

        // ACT
        subscriptionController.grantPremiumTier(originalUserId, originalTierId);

        // ASSERT
        verify(userSubscriptionService).grantPremiumTier(
                eq(UUID.fromString("12345678-1234-1234-1234-123456789012")),
                eq(UUID.fromString("87654321-4321-4321-4321-210987654321"))
        );
    }

    @Test
    @DisplayName("Should have correct HTTP status code value")
    void grantPremiumTier_whenSuccessful_returnsStatusCode200() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        ResponseEntity<Void> response = subscriptionController.grantPremiumTier(userId, tierId);

        // ASSERT
        assertEquals(200, response.getStatusCodeValue(),
                "Status code value should be 200");
        assertTrue(response.getStatusCode().is2xxSuccessful(),
                "Status should be in 2xx success range");
    }

    @Test
    @DisplayName("Should return ResponseEntity instance")
    void grantPremiumTier_whenCalled_returnsResponseEntityInstance() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        ResponseEntity<Void> response = subscriptionController.grantPremiumTier(userId, tierId);

        // ASSERT
        assertNotNull(response, "Response should not be null");
        assertInstanceOf(ResponseEntity.class, response,
                "Response should be instance of ResponseEntity");
    }
}
