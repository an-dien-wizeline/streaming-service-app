package io.github.marianciuc.streamingservice.subscription.service.impl;

import io.github.marianciuc.streamingservice.subscription.entity.Subscription;
import io.github.marianciuc.streamingservice.subscription.entity.SubscriptionStatus;
import io.github.marianciuc.streamingservice.subscription.entity.UserSubscriptions;
import io.github.marianciuc.streamingservice.subscription.exceptions.NotFoundException;
import io.github.marianciuc.streamingservice.subscription.repository.UserSubscriptionsRepository;
import io.github.marianciuc.streamingservice.subscription.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserSubscriptionServiceImpl.grantPremiumTier() method.
 * Tests cover upgrade scenarios, new subscription scenarios, error cases, and side effects.
 */
public class UserSubscriptionServiceImplGrantPremiumTierTest {

    // Mock dependencies
    private UserSubscriptionsRepository repository;
    private SubscriptionService subscriptionService;

    // System Under Test (SUT)
    private UserSubscriptionServiceImpl sut;

    // Test data
    private UUID userId;
    private UUID tierId;
    private Subscription premiumTier;
    private UserSubscriptions existingSubscription;

    @BeforeEach
    public void setUp() {
        // Initialize mocks
        repository = mock(UserSubscriptionsRepository.class);
        subscriptionService = mock(SubscriptionService.class);

        // Create SUT with mocked dependencies
        sut = new UserSubscriptionServiceImpl(repository, subscriptionService, null, null, null);

        // Initialize test data
        userId = UUID.randomUUID();
        tierId = UUID.randomUUID();

        premiumTier = new Subscription();
        premiumTier.setId(tierId);
        premiumTier.setName("Premium");
        premiumTier.setDurationInDays(30);

        existingSubscription = new UserSubscriptions();
        existingSubscription.setId(UUID.randomUUID());
        existingSubscription.setUserId(userId);
        existingSubscription.setStatus(SubscriptionStatus.ACTIVE);
        existingSubscription.setStartDate(LocalDate.now().minusDays(10));
        existingSubscription.setEndDate(LocalDate.now().plusDays(20));
    }

    /**
     * Test: User with active subscription gets upgraded to premium tier
     * Scenario: User has an existing active subscription
     * Expected: Existing subscription is updated with new tier and end date is recalculated
     */
    @Test
    public void grantPremiumTier_whenUserHasActiveSubscription_upgradesExistingSubscription() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        LocalDate expectedEndDate = LocalDate.now().plusDays(premiumTier.getDurationInDays());

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        ArgumentCaptor<UserSubscriptions> captor = ArgumentCaptor.forClass(UserSubscriptions.class);
        verify(repository, times(1)).save(captor.capture());

        UserSubscriptions savedSubscription = captor.getValue();
        assertEquals(premiumTier, savedSubscription.getSubscription(), "Subscription should be updated to premium tier");
        assertEquals(expectedEndDate, savedSubscription.getEndDate(), "End date should be recalculated based on premium tier duration");
        assertEquals(userId, savedSubscription.getUserId(), "User ID should remain unchanged");
        assertEquals(SubscriptionStatus.ACTIVE, savedSubscription.getStatus(), "Status should remain ACTIVE");
    }

    /**
     * Test: User without active subscription gets new premium subscription
     * Scenario: User has no existing active subscription
     * Expected: New subscription is created with premium tier
     */
    @Test
    public void grantPremiumTier_whenUserHasNoActiveSubscription_createsNewSubscription() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        ArgumentCaptor<UserSubscriptions> captor = ArgumentCaptor.forClass(UserSubscriptions.class);
        verify(repository, times(1)).save(captor.capture());

        UserSubscriptions savedSubscription = captor.getValue();
        assertNotNull(savedSubscription, "New subscription should be created");
        assertEquals(premiumTier, savedSubscription.getSubscription(), "Subscription should be set to premium tier");
        assertEquals(userId, savedSubscription.getUserId(), "User ID should match");
    }

    /**
     * Test: Invalid tier ID throws NotFoundException
     * Scenario: Tier ID does not exist in the system
     * Expected: NotFoundException is thrown from subscriptionService
     */
    @Test
    public void grantPremiumTier_whenTierIdNotFound_throwsNotFoundException() {
        // ARRANGE
        UUID invalidTierId = UUID.randomUUID();
        when(subscriptionService.getSubscription(invalidTierId))
                .thenThrow(new NotFoundException("Subscription tier not found"));

        // ACT & ASSERT
        assertThrows(NotFoundException.class, () -> {
            sut.grantPremiumTier(userId, invalidTierId);
        }, "Should throw NotFoundException when tier ID is invalid");

        // Verify repository was never called
        verify(repository, never()).save(any(UserSubscriptions.class));
    }

    /**
     * Test: End date calculation is correct for upgrade scenario
     * Scenario: User with active subscription is upgraded
     * Expected: End date is set to current date plus tier duration
     */
    @Test
    public void grantPremiumTier_whenUpgradingUser_calculatesEndDateCorrectly() {
        // ARRANGE
        int durationInDays = 60;
        premiumTier.setDurationInDays(durationInDays);

        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        LocalDate expectedEndDate = LocalDate.now().plusDays(durationInDays);

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        ArgumentCaptor<UserSubscriptions> captor = ArgumentCaptor.forClass(UserSubscriptions.class);
        verify(repository).save(captor.capture());

        UserSubscriptions savedSubscription = captor.getValue();
        assertEquals(expectedEndDate, savedSubscription.getEndDate(),
                "End date should be current date plus " + durationInDays + " days");
    }

    /**
     * Test: Repository save is called exactly once for upgrade scenario
     * Scenario: User with active subscription is upgraded
     * Expected: repository.save() is called exactly once
     */
    @Test
    public void grantPremiumTier_whenUpgradingUser_callsRepositorySaveOnce() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository, times(1)).save(any(UserSubscriptions.class));
        verify(subscriptionService, times(1)).getSubscription(tierId);
        verify(repository, times(1)).findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
    }

    /**
     * Test: Repository save is called exactly once for new subscription scenario
     * Scenario: User without active subscription gets new subscription
     * Expected: repository.save() is called exactly once
     */
    @Test
    public void grantPremiumTier_whenCreatingNewSubscription_callsRepositorySaveOnce() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository, times(1)).save(any(UserSubscriptions.class));
        verify(subscriptionService, times(1)).getSubscription(tierId);
        verify(repository, times(1)).findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
    }

    /**
     * Test: Subscription service is queried for the correct tier ID
     * Scenario: grantPremiumTier is called with specific tier ID
     * Expected: subscriptionService.getSubscription() is called with the same tier ID
     */
    @Test
    public void grantPremiumTier_whenCalled_queriesCorrectTierId() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        ArgumentCaptor<UUID> tierIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(subscriptionService).getSubscription(tierIdCaptor.capture());
        assertEquals(tierId, tierIdCaptor.getValue(), "Should query subscription service with correct tier ID");
    }

    /**
     * Test: Repository is queried for correct user ID and status
     * Scenario: grantPremiumTier is called with specific user ID
     * Expected: repository.findByUserIdAndStatus() is called with correct user ID and ACTIVE status
     */
    @Test
    public void grantPremiumTier_whenCalled_queriesRepositoryWithCorrectUserIdAndStatus() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<SubscriptionStatus> statusCaptor = ArgumentCaptor.forClass(SubscriptionStatus.class);
        verify(repository).findByUserIdAndStatus(userIdCaptor.capture(), statusCaptor.capture());

        assertEquals(userId, userIdCaptor.getValue(), "Should query repository with correct user ID");
        assertEquals(SubscriptionStatus.ACTIVE, statusCaptor.getValue(), "Should query for ACTIVE status only");
    }

    /**
     * Test: Original subscription ID is preserved during upgrade
     * Scenario: User with active subscription is upgraded
     * Expected: The subscription ID remains the same (in-place update)
     */
    @Test
    public void grantPremiumTier_whenUpgradingUser_preservesOriginalSubscriptionId() {
        // ARRANGE
        UUID originalSubscriptionId = existingSubscription.getId();

        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // ACT
        sut.grantPremiumTier(userId, tierId);

        // ASSERT
        ArgumentCaptor<UserSubscriptions> captor = ArgumentCaptor.forClass(UserSubscriptions.class);
        verify(repository).save(captor.capture());

        UserSubscriptions savedSubscription = captor.getValue();
        assertEquals(originalSubscriptionId, savedSubscription.getId(),
                "Original subscription ID should be preserved during upgrade");
    }

    /**
     * Test: Method handles different tier durations correctly
     * Scenario: Premium tiers with different durations (7, 30, 365 days)
     * Expected: End date is calculated correctly for each duration
     */
    @Test
    public void grantPremiumTier_whenDifferentTierDurations_calculatesEndDateCorrectly() {
        // Test with 7-day tier
        premiumTier.setDurationInDays(7);
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        sut.grantPremiumTier(userId, tierId);

        ArgumentCaptor<UserSubscriptions> captor = ArgumentCaptor.forClass(UserSubscriptions.class);
        verify(repository).save(captor.capture());
        assertEquals(LocalDate.now().plusDays(7), captor.getValue().getEndDate(),
                "Should calculate end date correctly for 7-day tier");

        // Reset for next test
        reset(repository, subscriptionService);

        // Test with 365-day tier
        premiumTier.setDurationInDays(365);
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        sut.grantPremiumTier(userId, tierId);

        verify(repository).save(captor.capture());
        assertEquals(LocalDate.now().plusDays(365), captor.getValue().getEndDate(),
                "Should calculate end date correctly for 365-day tier");
    }
}
