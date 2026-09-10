---
title: "Premium Tier Grant Feature"
description: "Documentation for the premium subscription tier grant functionality added in commit 32f1c47"
audience: developer
doc-type: reference
version: 1.0
last-updated: 2026-09-08
commit: 32f1c47841186df5b3d702d2e90ffaec785bebab
---

# Premium Tier Grant Feature

## Overview

This document describes the premium subscription tier grant functionality added to the subscription service. The feature enables administrators and backend systems to grant premium subscription tiers to users, either upgrading existing active subscriptions or creating new subscriptions when none exist.

**Key capability:** Grant a premium tier to a user with a single API call, with automatic handling of tier duration and subscription state management.

---

## What changed

Commit `32f1c47` introduces premium tier granting support to the subscription service with three coordinated changes:

### 1. New REST API endpoint

A new `POST /premium/grant` endpoint in `SubscriptionController` accepts a user ID and tier ID, then delegates to the service layer.

### 2. Service interface extension

The `UserSubscriptionService` interface adds a new contract method: `grantPremiumTier(UUID userId, UUID tierId)`.

### 3. Service implementation

`UserSubscriptionServiceImpl` implements the grant logic:
- Fetches the premium tier subscription definition
- Checks for an existing active subscription for the user
- **If active subscription exists:** upgrades it in place, updating the tier and recalculating the end date
- **If no active subscription exists:** creates a new user subscription with the premium tier

---

## Why this feature was added

Premium tier granting enables:

- **Administrative workflows** — support teams can manually grant premium access to users without requiring users to purchase through standard channels
- **Promotional campaigns** — backend systems can grant trial or promotional premium tiers programmatically
- **Subscription upgrades** — users can upgrade from lower tiers to premium within a single operation
- **Operational flexibility** — tier assignment is decoupled from payment processing, supporting use cases like corporate accounts, sponsored subscriptions, or dispute resolutions

---

## API endpoint

### Grant premium tier to a user

**Endpoint:** `POST /premium/grant`

**Purpose:** Grant a premium subscription tier to a user, upgrading their existing active subscription if present, or creating a new subscription if none exists.

#### Request

**Parameters:**

| Parameter | Type | Location | Required | Description |
|-----------|------|----------|----------|-------------|
| `userId` | UUID | Query | Yes | The UUID of the user receiving the premium tier |
| `tierId` | UUID | Query | Yes | The UUID of the premium subscription tier to grant |

**Example request:**

```http
POST /premium/grant?userId=550e8400-e29b-41d4-a716-446655440000&tierId=6ba7b810-9dad-11d1-80b4-00c04fd430c8
```

**cURL example:**

```bash
curl -X POST \
  "http://localhost:8080/premium/grant?userId=550e8400-e29b-41d4-a716-446655440000&tierId=6ba7b810-9dad-11d1-80b4-00c04fd430c8" \
  -H "Content-Type: application/json"
```

#### Response

**Status code:** `200 OK`

**Response body:** Empty (no content)

**Example response:**

```http
HTTP/1.1 200 OK
```

#### Error handling

| Status | Error condition | Resolution |
|--------|-----------------|-----------|
| `400` | Invalid UUID format for `userId` or `tierId` | Verify both parameters are valid UUIDs |
| `404` | Subscription tier not found (invalid `tierId`) | Confirm the tier ID exists in the system |
| `404` | User not found (invalid `userId`) | Confirm the user ID exists in the system |
| `500` | Internal server error during grant | Check application logs and retry; contact support if issue persists |

---

## Service layer implementation

### Method signature

```java
void grantPremiumTier(UUID userId, UUID tierId);
```

### Behavior

The `grantPremiumTier` method in `UserSubscriptionServiceImpl` executes the following logic:

#### Step 1: Fetch the tier definition

```java
Subscription tier = subscriptionService.getSubscription(tierId);
```

Retrieves the premium tier subscription definition. Throws an exception if the tier does not exist.

#### Step 2: Check for existing active subscription

```java
Optional<UserSubscriptions> current = repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
```

Queries the repository for any active subscription for the user.

#### Step 3: Upgrade or create

**If an active subscription exists:**

```java
if (current.isPresent()) {
    UserSubscriptions userSubscriptions = current.get();
    userSubscriptions.setSubscription(tier);
    userSubscriptions.setEndDate(LocalDate.now().plusDays(tier.getDurationInDays()));
    repository.save(userSubscriptions);
}
```

- Updates the subscription tier to the premium tier
- Recalculates the end date by adding the tier's duration (in days) to today's date
- Persists the changes

**If no active subscription exists:**

```java
else {
    repository.save(createUserSubscription(tier, userId, UUID.randomUUID()));
}
```

- Creates a new user subscription with the premium tier
- The new subscription is created with a fresh ID and inherits the tier's duration

### Code example

```java
// Inject UserSubscriptionService
@Autowired
private UserSubscriptionService userSubscriptionService;

// Grant premium tier to a user
UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
UUID tierId = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

userSubscriptionService.grantPremiumTier(userId, tierId);
```

---

## How to use the new functionality

### Scenario 1: Upgrade an existing active subscription

A user has an active standard tier subscription. An admin grants them a premium tier.

**Before:**
- User ID: `550e8400-e29b-41d4-a716-446655440000`
- Current subscription: Standard tier, expires 2026-12-31

**Request:**

```bash
curl -X POST \
  "http://localhost:8080/premium/grant?userId=550e8400-e29b-41d4-a716-446655440000&tierId=6ba7b810-9dad-11d1-80b4-00c04fd430c8"
```

**After:**
- Current subscription: Premium tier, expires 2026-09-08 + premium tier duration (e.g., 365 days = 2027-09-08)

### Scenario 2: Create a new subscription for a user with no active subscription

A user has no active subscription. A promotional campaign grants them a premium tier.

**Before:**
- User ID: `550e8400-e29b-41d4-a716-446655440000`
- Current subscription: None (or expired/cancelled)

**Request:**

```bash
curl -X POST \
  "http://localhost:8080/premium/grant?userId=550e8400-e29b-41d4-a716-446655440000&tierId=6ba7b810-9dad-11d1-80b4-00c04fd430c8"
```

**After:**
- Current subscription: Premium tier, newly created, expires 2026-09-08 + premium tier duration

---

## Integration notes

### Authentication and authorization

<!-- [GAP: The endpoint code shows no explicit authentication/authorization checks. Verify whether Spring Security annotations are present on the endpoint or inherited from the controller class. If authorization is required, document the required roles/permissions here.] -->

The endpoint should be protected with appropriate authentication and authorization checks. Typically, only administrators or backend services with elevated privileges should have access to grant premium tiers.

**Recommended:** Add role-based access control (RBAC) to restrict this endpoint to `ADMIN` or `SERVICE` roles.

### Transaction handling

The service method performs a database query and save operation. Ensure the method is wrapped in a transaction (typically via `@Transactional` annotation) to guarantee atomicity.

<!-- [GAP: Verify that the grantPremiumTier method is annotated with @Transactional or wrapped in a transactional context.] -->

### Error scenarios

**Tier not found:** If `tierId` does not exist, `subscriptionService.getSubscription(tierId)` throws an exception. Handle this at the controller level with appropriate HTTP error responses (e.g., 404 Not Found).

**User not found:** If `userId` does not exist as a valid user, the repository query will return an empty `Optional`. The method will create a new subscription for the user. Verify this is the desired behavior for your use case.

**Concurrent updates:** If multiple grant requests are made simultaneously for the same user, race conditions may occur. Consider adding pessimistic locking or optimistic concurrency checks to the repository.

<!-- [GAP: Verify concurrency handling strategy. If not implemented, document as a known limitation.] -->

### Subscription duration calculation

The end date is calculated as:

```
endDate = LocalDate.now().plusDays(tier.getDurationInDays())
```

This means:
- If the premium tier has a duration of 365 days, the subscription expires exactly 365 days from today
- The calculation is performed at grant time, not at subscription creation time
- Daylight saving time transitions are handled automatically by Java's `LocalDate` class

**Note:** If you grant a premium tier on 2026-09-08 with a 365-day duration, the end date will be 2027-09-08.

---

## Data model

### UserSubscriptions entity

The `UserSubscriptions` entity represents a user's subscription assignment. The grant operation modifies or creates records with these fields:

| Field | Type | Behavior during grant |
|-------|------|----------------------|
| `userId` | UUID | Set when creating new subscription; unchanged on upgrade |
| `subscription` | Subscription | Updated to the premium tier |
| `status` | SubscriptionStatus | Remains `ACTIVE` during upgrade; set to `ACTIVE` for new subscriptions |
| `endDate` | LocalDate | Recalculated as `LocalDate.now().plusDays(tier.getDurationInDays())` |
| `id` | UUID | Generated for new subscriptions; unchanged on upgrade |

### Subscription entity

The `Subscription` entity represents a tier definition. The grant operation reads:

| Field | Used for |
|-------|----------|
| `id` | Identifying the tier |
| `durationInDays` | Calculating the end date |
| (other fields) | Not used by the grant operation |

---

## Testing considerations

### Unit tests

Test the service method with these cases:

1. **Upgrade existing active subscription** — verify the tier is updated and end date is recalculated
2. **Create new subscription when none exists** — verify a new record is created with correct tier and end date
3. **Invalid tier ID** — verify appropriate exception is thrown
4. **Concurrent grant requests** — verify no race conditions or duplicate subscriptions

### Integration tests

Test the endpoint with these cases:

1. **Valid request with existing subscription** — verify 200 response and subscription updated
2. **Valid request with no existing subscription** — verify 200 response and subscription created
3. **Invalid UUID format** — verify 400 response
4. **Non-existent tier ID** — verify 404 response
5. **Non-existent user ID** — verify appropriate response (behavior depends on user validation logic)

### Example test case (pseudocode)

```java
@Test
public void testGrantPremiumTierUpgradesExistingSubscription() {
    // Arrange
    UUID userId = UUID.randomUUID();
    UUID tierId = UUID.randomUUID();
    UUID standardTierId = UUID.randomUUID();
    
    Subscription standardTier = new Subscription(standardTierId, "Standard", 30);
    Subscription premiumTier = new Subscription(tierId, "Premium", 365);
    
    UserSubscriptions existing = new UserSubscriptions(userId, standardTier, SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(30));
    
    // Act
    userSubscriptionService.grantPremiumTier(userId, tierId);
    
    // Assert
    UserSubscriptions updated = repository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE).get();
    assertEquals(premiumTier.getId(), updated.getSubscription().getId());
    assertEquals(LocalDate.now().plusDays(365), updated.getEndDate());
}
```

---

## Deployment considerations

### Backward compatibility

This change is backward compatible:
- Existing endpoints remain unchanged
- Existing subscriptions are not affected unless explicitly granted a new tier
- The new endpoint does not break any existing client code

### Database considerations

No database schema changes are required. The feature uses existing `UserSubscriptions` and `Subscription` tables.

### Configuration

Verify that:
- The subscription service is properly configured to access the subscription and user subscription repositories
- The tier IDs used in grant requests correspond to valid tiers in the database

---

## Related documentation

- [Subscription Service Architecture](./subscription-service-architecture.md) <!-- [GAP: Verify this file exists or update link] -->
- [Subscription Tiers Configuration](./subscription-tiers.md) <!-- [GAP: Verify this file exists or update link] -->
- [User Subscription Management API](./user-subscription-api.md) <!-- [GAP: Verify this file exists or update link] -->

---

## Changelog

### Version 1.0 (2026-09-08)

- **Added:** `POST /premium/grant` endpoint for granting premium subscription tiers
- **Added:** `grantPremiumTier(UUID userId, UUID tierId)` service method
- **Added:** Support for upgrading existing active subscriptions or creating new subscriptions during grant operations
- **Commit:** `32f1c47841186df5b3d702d2e90ffaec785bebab`

---

## Editor's notes

The following gaps should be verified and resolved:

1. **Authentication/authorization:** Confirm whether the endpoint requires authentication and what roles are authorized to call it. Add security annotations if not already present.

2. **Transaction handling:** Verify that the `grantPremiumTier` method is annotated with `@Transactional` to ensure database consistency.

3. **Concurrency handling:** Assess whether concurrent grant requests for the same user could cause race conditions. Implement locking if necessary.

4. **User validation:** Clarify the behavior when granting a tier to a non-existent user. Currently, a new subscription is created; confirm this is the intended behavior.

5. **Related documentation:** Verify that the linked documentation files exist and update paths if necessary.

6. **Error handling:** Review the controller implementation to ensure all error scenarios (invalid tier, invalid user, database errors) return appropriate HTTP status codes and error messages.
