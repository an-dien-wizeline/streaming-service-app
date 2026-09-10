package io.github.marianciuc.streamingservice.subscription.service.impl;

import io.github.marianciuc.streamingservice.subscription.entity.Subscription;
import io.github.marianciuc.streamingservice.subscription.entity.SubscriptionStatus;
import io.github.marianciuc.streamingservice.subscription.entity.UserSubscriptions;
import io.github.marianciuc.streamingservice.subscription.repository.UserSubscriptionRepository;
import io.github.marianciuc.streamingservice.subscription.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSubscriptionServiceImpl - Grant Premium Tier Tests")
class UserSubscriptionServiceImplGrantPremiumTierTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private UserSubscriptionRepository repository;

    @InjectMocks
    private UserSubscriptionServiceImpl userSubscriptionService;

    private UUID testUserId;
    private UUID testTierId;
    private Subscription premiumTier;
    private UserSubscriptions existingSubscription;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testTierId = UUID.randomUUID();

        // Setup premium tier
        premiumTier = Subscription.builder()
                .id(testTierId)
                .name("Premium")
                .description("Premium subscription")
                .durationInDays(30)
                .price(java.math.BigDecimal.valueOf(15.99))
                .allowedActiveSessions(4)
                .build();

        // Setup existing subscription
        existingSubscription = UserSubscriptions.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .orderId(UUID.randomUUID())
                .subscription(Subscription.builder()
                        .id(UUID.randomUUID())
                        .name("Standard")
                        .durationInDays(30)
                        .build())
                .startDate(LocalDate.now().minusDays(10))
                .endDate(LocalDate.now().plusDays(20))
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should upgrade existing active subscription to premium")
    void testGrantPremiumTier_WithExistingActiveSubscription_UpgradesSubscription() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(repository).save(any(UserSubscriptions.class));
        assertEquals(premiumTier, existingSubscription.getSubscription());
    }

    @Test
    @DisplayName("Should update end date when upgrading subscription")
    void testGrantPremiumTier_UpdatesEndDateCorrectly() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        LocalDate beforeGrant = LocalDate.now();

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        ArgumentCaptor<UserSubscriptions> captor = ArgumentCaptor.forClass(UserSubscriptions.class);
        verify(repository).save(captor.capture());
        UserSubscriptions saved = captor.getValue();

        LocalDate expectedEndDate = beforeGrant.plusDays(premiumTier.getDurationInDays());
        assertEquals(expectedEndDate, saved.getEndDate());
    }

    @Test
    @DisplayName("Should calculate end date as today plus tier duration")
    void testGrantPremiumTier_CalculatesEndDateAsTodayPlusDuration() {
        // Arrange
        Subscription tier30Days = Subscription.builder()
                .id(testTierId)
                .name("Premium 30")
                .durationInDays(30)
                .build();

        when(subscriptionService.getSubscription(testTierId)).thenReturn(tier30Days);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        LocalDate today = LocalDate.now();

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        ArgumentCaptor<UserSubscriptions> captor = ArgumentCaptor.forClass(UserSubscriptions.class);
        verify(repository).save(captor.capture());
        assertEquals(today.plusDays(30), captor.getValue().getEndDate());
    }

    @Test
    @DisplayName("Should create new subscription when user has no active subscription")
    void testGrantPremiumTier_WithNoExistingSubscription_CreatesNewSubscription() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSubscriptions.class))).thenReturn(new UserSubscriptions());

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(repository).save(any(UserSubscriptions.class));
    }

    @Test
    @DisplayName("Should fetch correct subscription tier by ID")
    void testGrantPremiumTier_FetchesCorrectTierById() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(subscriptionService).getSubscription(testTierId);
    }

    @Test
    @DisplayName("Should query repository for user's active subscription")
    void testGrantPremiumTier_QueriesForActiveSubscription() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(repository).findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should save subscription after upgrade")
    void testGrantPremiumTier_SavesSubscriptionAfterUpgrade() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(repository).save(any(UserSubscriptions.class));
    }

    @Test
    @DisplayName("Should throw exception when tier not found")
    void testGrantPremiumTier_WhenTierNotFound_ThrowsException() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId))
                .thenThrow(new IllegalArgumentException("Tier not found"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> userSubscriptionService.grantPremiumTier(testUserId, testTierId));
    }

    @Test
    @DisplayName("Should handle null userId")
    void testGrantPremiumTier_WithNullUserId_ThrowsException() {
        // Arrange & Act & Assert
        assertThrows(NullPointerException.class,
                () -> userSubscriptionService.grantPremiumTier(null, testTierId));
    }

    @Test
    @DisplayName("Should handle null tierId")
    void testGrantPremiumTier_WithNullTierId_ThrowsException() {
        // Arrange & Act & Assert
        assertThrows(NullPointerException.class,
                () -> userSubscriptionService.grantPremiumTier(testUserId, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 30, 90, 365})
    @DisplayName("Should correctly calculate end date for various tier durations")
    void testGrantPremiumTier_CalculatesEndDateForVariousDurations(int durationInDays) {
        // Arrange
        Subscription tier = Subscription.builder()
                .id(testTierId)
                .name("Test Tier")
                .durationInDays(durationInDays)
                .build();

        when(subscriptionService.getSubscription(testTierId)).thenReturn(tier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        LocalDate today = LocalDate.now();

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        ArgumentCaptor<UserSubscriptions> captor = ArgumentCaptor.forClass(UserSubscriptions.class);
        verify(repository).save(captor.capture());
        assertEquals(today.plusDays(durationInDays), captor.getValue().getEndDate());
    }

    @Test
    @DisplayName("Should preserve user ID when upgrading")
    void testGrantPremiumTier_PreservesUserIdWhenUpgrading() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        assertEquals(testUserId, existingSubscription.getUserId());
    }

    @Test
    @DisplayName("Should be idempotent when called multiple times")
    void testGrantPremiumTier_IsIdempotent() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(repository, times(3)).save(any(UserSubscriptions.class));
    }

    @Test
    @DisplayName("Should handle repository save failure")
    void testGrantPremiumTier_WhenRepositorySaveFails_PropagatesException() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> userSubscriptionService.grantPremiumTier(testUserId, testTierId));
    }

    @Test
    @DisplayName("Should call service method with correct parameters")
    void testGrantPremiumTier_CallsServiceMethodWithCorrectParameters() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(subscriptionService, times(1)).getSubscription(testTierId);
        verify(repository, times(1)).findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should not modify other user subscriptions")
    void testGrantPremiumTier_DoesNotAffectOtherUsers() {
        // Arrange
        UUID otherUserId = UUID.randomUUID();
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        verify(repository, never()).findByUserIdAndStatus(otherUserId, SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should set subscription on upgraded user subscription")
    void testGrantPremiumTier_SetsSubscriptionOnUserSubscription() {
        // Arrange
        when(subscriptionService.getSubscription(testTierId)).thenReturn(premiumTier);
        when(repository.findByUserIdAndStatus(testUserId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(repository.save(any(UserSubscriptions.class))).thenReturn(existingSubscription);

        // Act
        userSubscriptionService.grantPremiumTier(testUserId, testTierId);

        // Assert
        ArgumentCaptor<UserSubscriptions> captor = ArgumentCaptor.forClass(UserSubscriptions.class);
        verify(repository).save(captor.capture());
        assertEquals(premiumTier, captor.getValue().getSubscription());
    }
}
