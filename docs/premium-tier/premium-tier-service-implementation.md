---
title: "Premium Tier Service Implementation"
description: "Implementation guide for the grantPremiumTier service method"
audience: [developer, engineer]
doc-type: explanation
version: 1.0
last-updated: 2024-01-15
---

# Premium Tier Service Implementation

This document explains how the premium tier grant operation works internally and provides guidance for developers extending or maintaining the feature.

## Overview

The `grantPremiumTier()` method in `UserSubscriptionServiceImpl` handles two scenarios:

1. **Upgrade path**: User has an active subscription → replace it with the premium tier
2. **New subscription path**: User has no active subscription → create a new premium subscription

Both paths result in an active premium subscription with an end date calculated as today plus the tier's duration.

## Method signature

```java
@Override
public void grantPremiumTier(UUID userId, UUID tierId) {
    // Implementation
}
```

## Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `userId` | UUID | The user receiving the premium tier. Must not be null. |
| `tierId` | UUID | The premium tier to grant. Must not be null. References a valid `Subscription` record. |

## Implementation flow

### Step 1: Fetch the premium tier

```java
Subscription tier = subscriptionService.getSubscription(tierId);
```

Retrieves the premium tier definition by ID. Throws `IllegalArgumentException` if the tier does not exist.

**Why this step**: Validates that the requested tier exists before modifying user data. Fails fast if the tier ID is invalid.

### Step 2: Query for existing active subscription

```java
Optional<UserSubscriptions> current = 
    repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
```

Checks whether the user has an active subscription. Returns an `Optional` — empty if no active subscription exists.

**Why this step**: Determines which path to follow (upgrade vs. create new).

### Step 3a: Upgrade path (if active subscription exists)

```java
if (current.isPresent()) {
    UserSubscriptions userSubscriptions = current.get();
    userSubscriptions.setSubscription(tier);
    userSubscriptions.setEndDate(LocalDate.now().plusDays(tier.getDurationInDays()));
    repository.save(userSubscriptions);
}
```

**Actions:**
1. Retrieve the existing `UserSubscriptions` record
2. Replace the subscription reference with the premium tier
3. Recalculate the end date as today plus the tier's duration
4. Persist the changes

**Result**: The user's subscription is upgraded in place. The start date remains unchanged. The end date is extended.

**Example:**
- User had: Standard tier, ends 2024-02-15
- Grant: Premium tier (30 days)
- User now has: Premium tier, ends 2024-02-14 (today + 30 days)

### Step 3b: Create new subscription path (if no active subscription exists)

```java
else {
    repository.save(createUserSubscription(tier, userId, UUID.randomUUID()));
}
```

**Actions:**
1. Generate a new order ID (UUID)
2. Create a new `UserSubscriptions` record with:
   - The premium tier
   - Start date: today
   - End date: today plus the tier's duration
   - Status: ACTIVE
3. Persist the new record

**Result**: A new active premium subscription is created for the user.

## End date calculation

The end date is always calculated as:

```
end_date = LocalDate.now() + tier.durationInDays
```

This ensures that granting a premium tier always extends the user's access by exactly the tier's duration, regardless of whether they had a prior subscription.

## Idempotency guarantee

This method is **idempotent**. Calling it multiple times with the same parameters produces the same result:

- If an active subscription exists, it is upgraded to the premium tier and the end date is recalculated to today + duration.
- If no active subscription exists, a new one is created.

Repeated calls do not compound effects. The end date does not keep extending; it always resets to today + duration.

**Example:**
- Call 1: Grant premium (30 days) on 2024-01-15 → end date: 2024-02-14
- Call 2: Grant premium (30 days) on 2024-01-16 → end date: 2024-02-15 (not 2024-02-16)

## Error handling

### Null parameters

If either `userId` or `tierId` is null, a `NullPointerException` is thrown.

```java
// Throws NullPointerException
userSubscriptionService.grantPremiumTier(null, tierId);
userSubscriptionService.grantPremiumTier(userId, null);
```

**Recommendation**: Add explicit null checks and throw `IllegalArgumentException` with a descriptive message:

```java
if (userId == null) {
    throw new IllegalArgumentException("userId must not be null");
}
if (tierId == null) {
    throw new IllegalArgumentException("tierId must not be null");
}
```

### Invalid tier ID

If the tier ID does not exist, `subscriptionService.getSubscription(tierId)` throws `IllegalArgumentException`.

```java
// Throws IllegalArgumentException
userSubscriptionService.grantPremiumTier(userId, UUID.randomUUID());
```

**Recommendation**: Catch and wrap with a user-friendly message:

```java
try {
    Subscription tier = subscriptionService.getSubscription(tierId);
} catch (IllegalArgumentException e) {
    throw new SubscriptionNotFoundException("Premium tier not found: " + tierId, e);
}
```

### Database errors

If the repository save operation fails (e.g., database connection loss), a `RuntimeException` is thrown.

**Recommendation**: Implement retry logic or transaction management at the controller level.

## Testing strategy

The `UserSubscriptionServiceImplGrantPremiumTierTest` class provides 30 test cases covering:

- **Happy path**: Upgrade and create scenarios
- **Edge cases**: Various tier durations (1, 7, 30, 90, 365 days)
- **Error cases**: Null parameters, missing tier, database failures
- **Idempotency**: Multiple calls with same parameters
- **State preservation**: User ID and other fields unchanged

Run tests:

```bash
mvn test -Dtest=UserSubscriptionServiceImplGrantPremiumTierTest
```

## Performance considerations

### Database queries

The method performs:
1. One query to fetch the subscription tier
2. One query to find the user's active subscription
3. One insert or update operation

**Optimization opportunity**: Cache frequently accessed tiers to reduce database queries.

### Concurrent access

If two requests grant premium tiers to the same user simultaneously:
- Both will fetch the same active subscription
- Both will update it
- The second update will overwrite the first (last-write-wins)

**Recommendation**: Implement optimistic locking using a version field on `UserSubscriptions`:

```java
@Version
private Long version;
```

This prevents lost updates in concurrent scenarios.

## Integration points

### Dependencies

- **SubscriptionService**: Fetches the premium tier by ID
- **UserSubscriptionRepository**: Queries and persists user subscriptions
- **UserSubscriptions entity**: Represents a user's subscription record
- **Subscription entity**: Represents a subscription tier

### Callers

- **SubscriptionController.grantPremiumTier()**: HTTP endpoint handler
- Admin/operator services (for bulk grants)
- Billing or provisioning systems

## Related code

- `SubscriptionController.grantPremiumTier()` — HTTP endpoint
- `PremiumTierConfig` — Configuration properties
- `UserSubscriptions` entity — Data model
- `Subscription` entity — Tier definition

## Future enhancements

### Audit logging

Add audit events when premium tiers are granted:

```java
auditLog.log("PREMIUM_TIER_GRANTED", userId, tierId, LocalDateTime.now());
```

### Notifications

Send email or in-app notification to the user:

```java
notificationService.sendPremiumGrantNotification(userId, tier);
```

### Billing integration

Trigger billing events when premium is granted:

```java
billingService.recordPremiumGrant(userId, tier.getPrice());
```

### Rollback capability

Track previous subscription state to enable rollback:

```java
subscriptionAudit.recordChange(userId, previousTier, newTier);
```

---

## Editor's notes

- **Null safety**: Add explicit null checks with descriptive error messages.
- **Concurrency**: Implement optimistic locking to prevent lost updates.
- **Error handling**: Wrap low-level exceptions with domain-specific exceptions.
- **Audit trail**: Log all premium tier grants for compliance and debugging.
- **Notifications**: Clarify whether users should be notified of tier upgrades.
