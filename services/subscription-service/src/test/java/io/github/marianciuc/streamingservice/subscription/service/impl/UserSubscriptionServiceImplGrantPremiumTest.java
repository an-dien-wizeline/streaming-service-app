package io.github.marianciuc.streamingservice.subscription.service.impl;

import io.github.marianciuc.streamingservice.subscription.entity.Subscription;
import io.github.marianciuc.streamingservice.subscription.entity.SubscriptionStatus;
import io.github.marianciuc.streamingservice.subscription.entity.UserSubscriptions;
import io.github.marianciuc.streamingservice.subscription.exceptions.NotFoundException;
import io.github.marianciuc.streamingservice.subscription.repository.UserSubscriptionRepository;
import io.github.marianciuc.streamingservice.subscription.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserSubscriptionServiceImpl#grantPremiumTier(UUID, UUID)}.
 * 
 * Tests all scenarios for granting premium subscription tiers including:
 * - Upgrading existing active subscriptions
 * - Creating new subscriptions for users without active subscriptions
 * - Error handling for invalid tier IDs
 * - Boundary conditions and null handling
 * - Date calculations and repository interactions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserSubscriptionServiceImpl.grantPremiumTier Unit Tests")
class UserSubscriptionServiceImplGrantPremiumTest {

    @Mock
    private UserSubscriptionRepository repository;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private UserSubscriptionServiceImpl userSubscriptionService;

    @Captor
    private ArgumentCaptor<UserSubscriptions> userSubscriptionsCaptor;

    private UUID userId;
    private UUID tierId;
    private Subscription premiumTier;
    private Subscription basicTier;
    private UserSubscriptions existingSubscription;

    @BeforeEach
    void setUp() {
        // ARRANGE - Common test data
        userId = UUID.randomUUID();
        tierId = UUID.randomUUID();

        premiumTier = Subscription.builder()
                .id(tierId)
                .name("Premium")
                .description("Premium subscription with 4K streaming")
                .durationInDays(30)
                .price(new BigDecimal("19.99"))
                .allowedActiveSessions(4)
                .build();

        basicTier = Subscription.builder()
                .id(UUID.randomUUID())
                .name("Basic")
                .description("Basic subscription")
                .durationInDays(30)
                .price(new BigDecimal("9.99"))
                .allowedActiveSessions(1)
                .build();

        existingSubscription = UserSubscriptions.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .orderId(UUID.randomUUID())
                .subscription(basicTier)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(25))
                .build();
    }

    @Test
    @DisplayName("Should upgrade existing active subscription to premium tier")
    void grantPremiumTier_whenUserHasActiveSubscription_upgradesExistingSubscription() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(subscriptionService, times(1)).getSubscription(tierId);
        verify(repository, times(1)).findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        verify(repository, times(1)).save(userSubscriptionsCaptor.capture());

        UserSubscriptions savedSubscription = userSubscriptionsCaptor.getValue();
        assertEquals(premiumTier, savedSubscription.getSubscription(),
                "Subscription should be updated to premium tier");
        assertEquals(userId, savedSubscription.getUserId(),
                "User ID should remain unchanged");
        assertEquals(SubscriptionStatus.ACTIVE, savedSubscription.getStatus(),
                "Status should remain ACTIVE");
        assertNotNull(savedSubscription.getEndDate(),
                "End date should be set");
    }

    @Test
    @DisplayName("Should calculate correct end date when upgrading existing subscription")
    void grantPremiumTier_whenUpgradingExistingSubscription_calculatesCorrectEndDate() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate expectedEndDate = LocalDate.now().plusDays(premiumTier.getDurationInDays());

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository).save(userSubscriptionsCaptor.capture());
        UserSubscriptions savedSubscription = userSubscriptionsCaptor.getValue();
        assertEquals(expectedEndDate, savedSubscription.getEndDate(),
                "End date should be calculated as now + tier duration in days");
    }

    @Test
    @DisplayName("Should create new subscription when user has no active subscription")
    void grantPremiumTier_whenUserHasNoActiveSubscription_createsNewSubscription() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(subscriptionService, times(1)).getSubscription(tierId);
        verify(repository, times(1)).findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        verify(repository, times(1)).save(userSubscriptionsCaptor.capture());

        UserSubscriptions savedSubscription = userSubscriptionsCaptor.getValue();
        assertEquals(premiumTier, savedSubscription.getSubscription(),
                "New subscription should have premium tier");
        assertEquals(userId, savedSubscription.getUserId(),
                "User ID should be set correctly");
        assertEquals(SubscriptionStatus.ACTIVE, savedSubscription.getStatus(),
                "Status should be ACTIVE");
        assertNotNull(savedSubscription.getOrderId(),
                "Order ID should be generated");
        assertEquals(LocalDate.now(), savedSubscription.getStartDate(),
                "Start date should be today");
    }

    @Test
    @DisplayName("Should calculate correct end date when creating new subscription")
    void grantPremiumTier_whenCreatingNewSubscription_calculatesCorrectEndDate() {
        // ARRANGE
        Subscription longTermTier = Subscription.builder()
                .id(tierId)
                .name("Premium Annual")
                .description("Premium annual subscription")
                .durationInDays(365)
                .price(new BigDecimal("199.99"))
                .allowedActiveSessions(4)
                .build();

        when(subscriptionService.getSubscription(tierId)).thenReturn(longTermTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate expectedEndDate = LocalDate.now().plusDays(365);

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository).save(userSubscriptionsCaptor.capture());
        UserSubscriptions savedSubscription = userSubscriptionsCaptor.getValue();
        assertEquals(expectedEndDate, savedSubscription.getEndDate(),
                "End date should be calculated as now + 365 days");
    }

    @Test
    @DisplayName("Should throw NotFoundException when tier ID is invalid")
    void grantPremiumTier_whenTierIdIsInvalid_throwsNotFoundException() {
        // ARRANGE
        UUID invalidTierId = UUID.randomUUID();
        when(subscriptionService.getSubscription(invalidTierId))
                .thenThrow(new NotFoundException("Subscription tier not found"));

        // ACT & ASSERT
        assertThrows(NotFoundException.class,
                () -> userSubscriptionService.grantPremiumTier(userId, invalidTierId),
                "Should throw NotFoundException for invalid tier ID");

        verify(subscriptionService, times(1)).getSubscription(invalidTierId);
        verify(repository, never()).findByUserIdAndStatus(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle null userId gracefully")
    void grantPremiumTier_whenUserIdIsNull_throwsNullPointerException() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(null, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(NullPointerException.class,
                () -> userSubscriptionService.grantPremiumTier(null, tierId),
                "Should throw NullPointerException when userId is null");
    }

    @Test
    @DisplayName("Should handle null tierId gracefully")
    void grantPremiumTier_whenTierIdIsNull_throwsException() {
        // ARRANGE
        when(subscriptionService.getSubscription(null))
                .thenThrow(new IllegalArgumentException("Tier ID cannot be null"));

        // ACT & ASSERT
        assertThrows(IllegalArgumentException.class,
                () -> userSubscriptionService.grantPremiumTier(userId, null),
                "Should throw exception when tierId is null");

        verify(subscriptionService, times(1)).getSubscription(null);
        verify(repository, never()).findByUserIdAndStatus(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should only call repository save once when upgrading")
    void grantPremiumTier_whenUpgrading_callsRepositorySaveOnce() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository, times(1)).save(any(UserSubscriptions.class));
    }

    @Test
    @DisplayName("Should only call repository save once when creating new subscription")
    void grantPremiumTier_whenCreatingNew_callsRepositorySaveOnce() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository, times(1)).save(any(UserSubscriptions.class));
    }

    @Test
    @DisplayName("Should preserve original order ID when upgrading existing subscription")
    void grantPremiumTier_whenUpgrading_preservesOriginalOrderId() {
        // ARRANGE
        UUID originalOrderId = existingSubscription.getOrderId();
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository).save(userSubscriptionsCaptor.capture());
        UserSubscriptions savedSubscription = userSubscriptionsCaptor.getValue();
        assertEquals(originalOrderId, savedSubscription.getOrderId(),
                "Original order ID should be preserved during upgrade");
    }

    @Test
    @DisplayName("Should generate new order ID when creating new subscription")
    void grantPremiumTier_whenCreatingNew_generatesNewOrderId() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository).save(userSubscriptionsCaptor.capture());
        UserSubscriptions savedSubscription = userSubscriptionsCaptor.getValue();
        assertNotNull(savedSubscription.getOrderId(),
                "Order ID should be generated for new subscription");
    }

    @Test
    @DisplayName("Should set start date to today when creating new subscription")
    void grantPremiumTier_whenCreatingNew_setsStartDateToToday() {
        // ARRANGE
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate today = LocalDate.now();

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository).save(userSubscriptionsCaptor.capture());
        UserSubscriptions savedSubscription = userSubscriptionsCaptor.getValue();
        assertEquals(today, savedSubscription.getStartDate(),
                "Start date should be set to today for new subscription");
    }

    @Test
    @DisplayName("Should not modify start date when upgrading existing subscription")
    void grantPremiumTier_whenUpgrading_doesNotModifyStartDate() {
        // ARRANGE
        LocalDate originalStartDate = existingSubscription.getStartDate();
        when(subscriptionService.getSubscription(tierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository).save(userSubscriptionsCaptor.capture());
        UserSubscriptions savedSubscription = userSubscriptionsCaptor.getValue();
        assertEquals(originalStartDate, savedSubscription.getStartDate(),
                "Start date should not be modified during upgrade");
    }

    @Test
    @DisplayName("Should handle tier with zero duration days")
    void grantPremiumTier_whenTierHasZeroDuration_setsEndDateToToday() {
        // ARRANGE
        Subscription zeroDurationTier = Subscription.builder()
                .id(tierId)
                .name("Trial")
                .description("Zero day trial")
                .durationInDays(0)
                .price(BigDecimal.ZERO)
                .allowedActiveSessions(1)
                .build();

        when(subscriptionService.getSubscription(tierId)).thenReturn(zeroDurationTier);
        when(repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate expectedEndDate = LocalDate.now().plusDays(0);

        // ACT
        userSubscriptionService.grantPremiumTier(userId, tierId);

        // ASSERT
        verify(repository).save(userSubscriptionsCaptor.capture());
        UserSubscriptions savedSubscription = userSubscriptionsCaptor.getValue();
        assertEquals(expectedEndDate, savedSubscription.getEndDate(),
                "End date should be today when duration is 0 days");
    }
}
