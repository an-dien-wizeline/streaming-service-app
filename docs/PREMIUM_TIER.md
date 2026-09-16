---
title: "Premium Subscription Tier Feature"
description: "Comprehensive guide to the premium subscription tier feature, including API reference, configuration, security considerations, and integration guidelines"
audience: developer
doc-type: explanation
version: 1.0
last-updated: 2025-01-15
status: ⚠️ SECURITY REVIEW REQUIRED
---

# Premium subscription tier feature

The premium subscription tier feature enables administrators to grant premium-level subscriptions to users, providing enhanced streaming capabilities including 4K video playback and increased concurrent session limits.

## Overview

### What this feature does

The premium tier system allows designated users to receive upgraded subscription benefits without going through the standard payment flow. When a premium tier is granted:

- The user's existing active subscription is upgraded in place, or
- A new premium subscription is created if no active subscription exists
- The subscription end date is automatically calculated based on the tier's duration
- Premium entitlements are made available to downstream services (e.g., media service for 4K playback)

### Business context

Premium tier grants support several use cases:

- **Promotional campaigns** — award premium access to contest winners or influencers
- **Customer service recovery** — compensate users for service issues
- **Employee benefits** — provide internal staff with premium access
- **Partnership agreements** — fulfill obligations to partner organizations

### Architecture integration

The premium tier feature integrates with:

- **Subscription service** — manages tier assignment and subscription lifecycle
- **Media service** — consumes entitlement tokens to enable 4K streaming (signed with `ENTITLEMENT_SIGNING_KEY`)
- **Partner billing gateway** — processes premium-specific billing operations (authenticated with `PARTNER_BILLING_API_KEY`)

---

## 🚨 Critical security warning

**The `/api/v1/subscription/premium/grant` endpoint currently has NO authentication or authorization controls.**

### Impact

Any user or system with network access to the subscription service can:

- Grant premium subscriptions to any user ID
- Bypass payment processing entirely
- Create financial liability through unauthorized premium access
- Abuse the system for account takeover or privilege escalation

### Risk severity

**CRITICAL** — This vulnerability allows unauthorized privilege escalation and revenue loss.

### Current state

```java
@PostMapping("/premium/grant")
public ResponseEntity<Void> grantPremiumTier(@RequestParam("userId") UUID userId,
                                             @RequestParam("tierId") UUID tierId) {
    userSubscriptionService.grantPremiumTier(userId, tierId);
    return ResponseEntity.ok().build();
}
```

**No `@PreAuthorize` annotation is present.** Compare with other admin endpoints in the same controller:

```java
@PostMapping
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")  // ✅ Protected
public ResponseEntity<SubscriptionResponse> addSubscription(@RequestBody @Valid SubscriptionRequest request) {
    return ResponseEntity.ok(subscriptionService.createSubscription(request));
}
```

### Recommended fix

Add role-based access control immediately:

```java
@PostMapping("/premium/grant")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")  // Add this line
public ResponseEntity<Void> grantPremiumTier(@RequestParam("userId") UUID userId,
                                             @RequestParam("tierId") UUID tierId) {
    userSubscriptionService.grantPremiumTier(userId, tierId);
    return ResponseEntity.ok().build();
}
```

**Until this fix is deployed, the endpoint MUST NOT be exposed to production networks.**

---

## Configuration

### Application configuration

The premium tier feature is configured in `application.yml`:

```yaml
premium-tier:
  enabled: true
  allowed-sessions: 4
  entitlement-signing-key: "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d"
  partner-billing-api-key: "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d"
```

### Configuration properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `premium-tier.enabled` | boolean | `true` | Feature flag to enable/disable premium tier functionality |
| `premium-tier.allowed-sessions` | integer | `4` | Number of concurrent streaming sessions allowed for premium users |
| `premium-tier.entitlement-signing-key` | string | (required) | Shared secret used to sign JWT entitlement tokens for the media service |
| `premium-tier.partner-billing-api-key` | string | (required) | API key for authenticating with the partner billing gateway |

### 🔐 Security warning: hardcoded secrets

**Critical issue:** Sensitive credentials are hardcoded in both the configuration file and `PremiumTierConfig.java`.

#### Current implementation

```java
@Configuration
@Getter
public class PremiumTierConfig {
    private static final String ENTITLEMENT_SIGNING_KEY =
            "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d";

    private static final String PARTNER_BILLING_API_KEY =
            "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d";
    // ...
}
```

#### Problems

- Secrets are committed to version control (visible in Git history)
- No rotation mechanism exists
- Secrets are visible to anyone with repository access
- Violates least-privilege principle

#### Recommended approach

Use Spring Boot's externalized configuration with environment variables:

**Step 1:** Remove hardcoded secrets from `PremiumTierConfig.java`:

```java
@Configuration
@ConfigurationProperties(prefix = "premium-tier")
@Getter
@Setter
public class PremiumTierConfig {
    private boolean enabled;
    private int allowedSessions;
    private String entitlementSigningKey;
    private String partnerBillingApiKey;
}
```

**Step 2:** Load from environment variables in production:

```bash
export PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY="<secret-from-vault>"
export PREMIUM_TIER_PARTNER_BILLING_API_KEY="<secret-from-vault>"
```

**Step 3:** Reference in `application.yml`:

```yaml
premium-tier:
  enabled: ${PREMIUM_TIER_ENABLED:true}
  allowed-sessions: ${PREMIUM_TIER_ALLOWED_SESSIONS:4}
  entitlement-signing-key: ${PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY}
  partner-billing-api-key: ${PREMIUM_TIER_PARTNER_BILLING_API_KEY}
```

**Step 4:** Rotate the exposed secrets immediately.

---

## API reference

### Grant premium tier

Assigns a premium subscription tier to a user.

**Endpoint:** `POST /api/v1/subscription/premium/grant`

**Authentication:** ⚠️ **NONE (CRITICAL VULNERABILITY)** — see [Security Warning](#-critical-security-warning)

**Content-Type:** `application/x-www-form-urlencoded` or `application/json` (query parameters)

#### Request parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `userId` | UUID | Yes | The unique identifier of the user receiving the premium tier |
| `tierId` | UUID | Yes | The unique identifier of the premium subscription tier to grant |

#### Behavior

The endpoint follows this logic:

1. **Validate tier existence** — fetches the subscription tier by `tierId`; throws `NotFoundException` if not found
2. **Check for active subscription** — queries for an existing active subscription for `userId`
3. **Upgrade or create:**
   - **If active subscription exists:** upgrades it by replacing the subscription reference and recalculating the end date
   - **If no active subscription exists:** creates a new `UserSubscriptions` record with a generated order ID

#### Example request (curl)

```bash
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

#### Example request (HTTP)

```http
POST /api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded
```

#### Example request (Java)

```java
RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8080/api/v1/subscription/premium/grant";

UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
    .queryParam("userId", "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    .queryParam("tierId", "f1e2d3c4-b5a6-7890-cdef-123456789abc");

ResponseEntity<Void> response = restTemplate.postForEntity(
    builder.toUriString(),
    null,
    Void.class
);
```

#### Success response

**Status:** `200 OK`

**Body:** Empty (no content)

```http
HTTP/1.1 200 OK
Content-Length: 0
```

#### Error responses

| Status | Condition | Response Body | Resolution |
|--------|-----------|---------------|------------|
| 400 Bad Request | Invalid UUID format for `userId` or `tierId` | `{"error": "Invalid UUID string: <value>"}` | Provide valid UUID v4 strings |
| 404 Not Found | Subscription tier with `tierId` does not exist | `{"error": "Subscription tier not found"}` | Verify the tier ID exists in the `subscriptions` table |
| 500 Internal Server Error | Database connection failure or unexpected error | `{"error": "Internal server error"}` | Check application logs; verify database connectivity |

#### State changes

**Scenario 1: User has an active subscription**

Before:
```
UserSubscriptions {
  id: "sub-123",
  userId: "a1b2c3d4-...",
  subscription: { id: "tier-basic", name: "Basic", durationInDays: 30 },
  startDate: "2025-01-01",
  endDate: "2025-01-31",
  status: ACTIVE
}
```

After calling `/premium/grant` with `tierId=tier-premium` (90-day duration):
```
UserSubscriptions {
  id: "sub-123",  // Same record
  userId: "a1b2c3d4-...",
  subscription: { id: "tier-premium", name: "Premium", durationInDays: 90 },  // Updated
  startDate: "2025-01-01",  // Unchanged
  endDate: "2025-04-15",  // Recalculated: now() + 90 days
  status: ACTIVE
}
```

**Scenario 2: User has no active subscription**

Before:
```
(No active subscription record for user)
```

After calling `/premium/grant`:
```
UserSubscriptions {
  id: "<generated-uuid>",
  userId: "a1b2c3d4-...",
  orderId: "<generated-uuid>",  // Random UUID, not tied to payment
  subscription: { id: "tier-premium", name: "Premium", durationInDays: 90 },
  startDate: "2025-01-15",  // Today
  endDate: "2025-04-15",  // now() + 90 days
  status: ACTIVE
}
```

---

## Integration guide

### For internal services

If you are building a service that needs to verify premium entitlements:

#### Step 1: Obtain the entitlement signing key

The `ENTITLEMENT_SIGNING_KEY` is shared between the subscription service and your service. Retrieve it from your environment configuration:

```java
@Value("${premium-tier.entitlement-signing-key}")
private String entitlementSigningKey;
```

#### Step 2: Verify entitlement tokens

When the subscription service issues an entitlement token (JWT), verify it using the shared key:

```java
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

public boolean isPremiumUser(String token) {
    try {
        Claims claims = Jwts.parser()
            .setSigningKey(entitlementSigningKey)
            .parseClaimsJws(token)
            .getBody();
        
        return "premium".equals(claims.get("tier"));
    } catch (Exception e) {
        return false;
    }
}
```

#### Step 3: Enforce premium-only features

Example: Enable 4K streaming only for premium users:

```java
public Resolution getMaxResolution(String entitlementToken) {
    if (isPremiumUser(entitlementToken)) {
        return Resolution.UHD_4K;
    }
    return Resolution.HD_1080P;
}
```

### For external partner billing gateway

The subscription service authenticates with the partner billing gateway using the `PARTNER_BILLING_API_KEY`.

#### Expected API contract

The partner billing gateway should expose an endpoint to record premium grants:

**Endpoint:** `POST https://billing.partner.example.com/v1/premium-grants`

**Headers:**
```
Authorization: Bearer wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d
Content-Type: application/json
```

**Request body:**
```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "tierId": "f1e2d3c4-b5a6-7890-cdef-123456789abc",
  "grantedAt": "2025-01-15T14:30:00Z",
  "expiresAt": "2025-04-15T14:30:00Z"
}
```

**Success response:**
```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "grantId": "grant_abc123",
  "status": "recorded"
}
```

---

## Data model

### Database schema

The premium tier feature interacts with these tables:

#### `subscriptions`

Stores subscription tier definitions (including premium tiers).

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | UUID | No | Primary key |
| `name` | VARCHAR | No | Tier name (e.g., "Premium") |
| `description` | TEXT | No | Marketing description |
| `duration_in_days` | INTEGER | No | Subscription duration |
| `price` | DECIMAL | No | Tier price |
| `currency` | VARCHAR | No | Price currency (USD, EUR, etc.) |
| `allowed_active_sessions` | INTEGER | Yes | Concurrent session limit |
| `record_status` | VARCHAR | Yes | ACTIVE, DELETED, HIDDEN |
| `is_temporary` | BOOLEAN | Yes | Whether tier is temporary |
| `next_subscription_id` | UUID | Yes | Next tier after expiration (for temporary tiers) |
| `created_at` | TIMESTAMP | No | Creation timestamp |
| `updated_at` | TIMESTAMP | No | Last update timestamp |

#### `user_subscriptions`

Stores user subscription assignments.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | UUID | No | Primary key |
| `user_id` | UUID | No | Foreign key to users table |
| `order_id` | UUID | No | Foreign key to orders table (or generated UUID for grants) |
| `subscription_id` | UUID | No | Foreign key to `subscriptions.id` |
| `start_date` | DATE | Yes | Subscription start date |
| `end_date` | DATE | Yes | Subscription end date |
| `status` | VARCHAR | Yes | ACTIVE, INACTIVE, PENDING, EXPIRED, CANCELLED |

### Entity relationships

```
subscriptions (1) ──< (N) user_subscriptions
     ↑                        ↓
     │                    user_id (references users.id)
     │                    order_id (references orders.id or generated)
     └── subscription_id
```

### Subscription status lifecycle

```
PENDING ──[payment success]──> ACTIVE
   │                              │
   └──[payment failed]──> INACTIVE│
                                  │
                          [end date reached]
                                  │
                                  ↓
                              EXPIRED
                                  │
                          [user cancels]
                                  ↓
                             CANCELLED
```

When `grantPremiumTier()` is called:
- Existing ACTIVE subscription → updated in place (status remains ACTIVE)
- No active subscription → new record created with status ACTIVE

---

## Service implementation

### Method signature

```java
package io.github.marianciuc.streamingservice.subscription.service;

public interface UserSubscriptionService {
    /**
     * Grants a premium subscription tier to a user.
     *
     * @param userId the UUID of the user receiving the premium tier
     * @param tierId the UUID of the premium tier to grant
     * @throws NotFoundException if the tier ID does not exist
     */
    void grantPremiumTier(UUID userId, UUID tierId);
}
```

### Implementation logic

```java
@Override
public void grantPremiumTier(UUID userId, UUID tierId) {
    // Step 1: Fetch the subscription tier (throws NotFoundException if not found)
    Subscription tier = subscriptionService.getSubscription(tierId);
    
    // Step 2: Check for existing active subscription
    Optional<UserSubscriptions> current = repository.findByUserIdAndStatus(
        userId, 
        SubscriptionStatus.ACTIVE
    );

    if (current.isPresent()) {
        // Scenario A: Upgrade existing subscription
        UserSubscriptions userSubscriptions = current.get();
        userSubscriptions.setSubscription(tier);
        userSubscriptions.setEndDate(
            LocalDate.now().plusDays(tier.getDurationInDays())
        );
        repository.save(userSubscriptions);
    } else {
        // Scenario B: Create new subscription
        repository.save(
            createUserSubscription(tier, userId, UUID.randomUUID())
        );
    }
}
```

### Key behaviors

1. **No payment processing** — the method bypasses the order/payment flow entirely
2. **Order ID generation** — for new subscriptions, a random UUID is assigned to `orderId` (not tied to an actual order)
3. **End date calculation** — always recalculated as `now() + tier.durationInDays`, even for upgrades (this *replaces* the remaining time on the old subscription)
4. **Idempotency** — calling the endpoint multiple times with the same parameters updates the same record (if an active subscription exists) or creates duplicate records (if none exists)

### Transaction behavior

The method is not explicitly marked `@Transactional`, but Spring Data JPA's `save()` operations are transactional by default. The entire operation (fetch tier, check existing, save) executes within a single transaction.

---

## Error handling

### Exception types

| Exception | Thrown When | HTTP Status | Message |
|-----------|-------------|-------------|---------|
| `NotFoundException` | `tierId` does not exist in `subscriptions` table | 404 | "Subscription tier not found" |
| `NotFoundException` | `userId` does not exist (if user validation is added) | 404 | "User not found" |
| `DataIntegrityViolationException` | Database constraint violation (e.g., duplicate order ID) | 500 | (varies by constraint) |
| `IllegalArgumentException` | Invalid UUID format | 400 | "Invalid UUID string: <value>" |

### Error response format

The subscription service uses a global exception handler. Error responses follow this structure:

```json
{
  "timestamp": "2025-01-15T14:30:00.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "Subscription tier not found",
  "path": "/api/v1/subscription/premium/grant"
}
```

### Handling errors in client code

```java
try {
    ResponseEntity<Void> response = restTemplate.postForEntity(url, null, Void.class);
    if (response.getStatusCode().is2xxSuccessful()) {
        System.out.println("Premium tier granted successfully");
    }
} catch (HttpClientErrorException.NotFound e) {
    System.err.println("Tier or user not found: " + e.getResponseBodyAsString());
} catch (HttpClientErrorException.BadRequest e) {
    System.err.println("Invalid request parameters: " + e.getResponseBodyAsString());
} catch (HttpServerErrorException e) {
    System.err.println("Server error: " + e.getResponseBodyAsString());
}
```

---

## Testing

### Unit tests

Unit tests are located at:
```
services/subscription-service/src/test/java/io/github/marianciuc/streamingservice/subscription/controller/SubscriptionControllerGrantPremiumTierTest.java
```

**Test coverage includes:**
- ✅ Valid request returns 200 OK
- ✅ Service method invoked with correct parameters
- ✅ `NotFoundException` propagates correctly
- ✅ Multiple invocations handled correctly
- ✅ Response entity structure validated

### Integration testing

To test the endpoint in a local or staging environment:

#### Prerequisites

1. Subscription service running on `http://localhost:8080`
2. PostgreSQL database accessible
3. At least one subscription tier record in the `subscriptions` table

#### Test scenario: Grant premium to new user

**Step 1:** Create a test premium tier (if not exists):

```sql
INSERT INTO subscriptions (id, name, description, duration_in_days, price, currency, allowed_active_sessions, record_status, is_temporary, created_at, updated_at)
VALUES (
  'f1e2d3c4-b5a6-7890-cdef-123456789abc',
  'Premium',
  'Premium tier with 4K streaming and 4 concurrent sessions',
  90,
  19.99,
  'USD',
  4,
  'ACTIVE',
  false,
  NOW(),
  NOW()
);
```

**Step 2:** Grant premium tier to a user:

```bash
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc"
```

**Step 3:** Verify the subscription was created:

```sql
SELECT * FROM user_subscriptions WHERE user_id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
```

Expected result:
```
id                  | user_id             | subscription_id     | status | start_date | end_date
--------------------|---------------------|---------------------|--------|------------|------------
<generated-uuid>    | a1b2c3d4-...        | f1e2d3c4-...        | ACTIVE | 2025-01-15 | 2025-04-15
```

#### Test scenario: Upgrade existing subscription

**Step 1:** Create a basic subscription for the user:

```sql
INSERT INTO user_subscriptions (id, user_id, order_id, subscription_id, start_date, end_date, status)
VALUES (
  'sub-basic-123',
  'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
  'order-123',
  '<basic-tier-id>',
  '2025-01-01',
  '2025-01-31',
  'ACTIVE'
);
```

**Step 2:** Grant premium tier:

```bash
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc"
```

**Step 3:** Verify the subscription was upgraded (not duplicated):

```sql
SELECT * FROM user_subscriptions WHERE user_id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890' AND status = 'ACTIVE';
```

Expected result (single record):
```
id          | subscription_id     | end_date
------------|---------------------|------------
sub-basic-123 | f1e2d3c4-...      | 2025-04-15  -- Updated to premium tier and extended
```

---

## Deployment guide

### Pre-deployment checklist

Before deploying the premium tier feature to production:

- [ ] **CRITICAL:** Add `@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")` to the `/premium/grant` endpoint
- [ ] Rotate all hardcoded secrets (`ENTITLEMENT_SIGNING_KEY`, `PARTNER_BILLING_API_KEY`)
- [ ] Move secrets to environment variables or secret management system (AWS Secrets Manager, HashiCorp Vault, etc.)
- [ ] Update `PremiumTierConfig.java` to use `@ConfigurationProperties` instead of hardcoded values
- [ ] Add audit logging for all premium tier grants (who granted, when, to whom)
- [ ] Configure network-level access controls (firewall rules, API gateway policies)
- [ ] Test the endpoint with invalid tier IDs and user IDs
- [ ] Verify database indexes exist on `user_subscriptions(user_id, status)` for query performance
- [ ] Document the premium tier grant process in your runbook
- [ ] Train customer service team on when/how to use the feature

### Environment variables

Set these environment variables in your deployment environment:

```bash
# Feature flag
export PREMIUM_TIER_ENABLED=true

# Session limits
export PREMIUM_TIER_ALLOWED_SESSIONS=4

# Secrets (retrieve from vault)
export PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY="<secret>"
export PREMIUM_TIER_PARTNER_BILLING_API_KEY="<secret>"
```

### Database migrations

No database migrations are required — the feature uses existing tables (`subscriptions`, `user_subscriptions`).

### Rollback plan

If issues arise after deployment:

1. **Disable the feature flag:**
   ```bash
   export PREMIUM_TIER_ENABLED=false
   ```

2. **Revert the code changes** if the feature flag is not sufficient:
   ```bash
   git revert <commit-hash>
   ```

3. **Revoke granted premium tiers** (if necessary):
   ```sql
   UPDATE user_subscriptions
   SET status = 'CANCELLED'
   WHERE subscription_id IN (SELECT id FROM subscriptions WHERE name = 'Premium')
     AND start_date >= '<deployment-date>';
   ```

---

## Monitoring and observability

### Metrics to track

Instrument the following metrics in your monitoring system (Prometheus, Datadog, etc.):

| Metric | Type | Description |
|--------|------|-------------|
| `premium_tier_grants_total` | Counter | Total number of premium tier grants |
| `premium_tier_grant_errors_total` | Counter | Failed grant attempts (by error type) |
| `premium_tier_grant_duration_seconds` | Histogram | Time taken to grant premium tier |
| `premium_subscriptions_active` | Gauge | Current count of active premium subscriptions |

### Logging

Ensure the following events are logged:

```java
log.info("Premium tier granted: userId={}, tierId={}, grantedBy={}", userId, tierId, adminId);
log.warn("Premium tier grant failed: userId={}, tierId={}, error={}", userId, tierId, e.getMessage());
```

### Alerts

Configure alerts for:

- **Spike in premium grants** — more than X grants per hour (potential abuse)
- **High error rate** — more than 5% of grant attempts failing
- **Unauthorized access attempts** — 401/403 responses on the endpoint (once authentication is added)

---

## FAQ

### Why does the endpoint use query parameters instead of a request body?

The current implementation uses `@RequestParam`, which is typical for simple POST requests with few parameters. For consistency with RESTful conventions, consider refactoring to use a request body:

```java
@PostMapping("/premium/grant")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
public ResponseEntity<Void> grantPremiumTier(@RequestBody @Valid PremiumGrantRequest request) {
    userSubscriptionService.grantPremiumTier(request.getUserId(), request.getTierId());
    return ResponseEntity.ok().build();
}
```

### What happens to the user's remaining subscription time when upgraded?

**The remaining time is lost.** The `grantPremiumTier()` method recalculates the end date as `now() + tier.durationInDays`, overwriting the previous end date.

**Example:**
- User has 20 days remaining on a Basic subscription
- Premium tier (90 days) is granted
- New end date: today + 90 days (the 20 days are not added)

**Recommendation:** If you want to preserve remaining time, modify the logic:

```java
if (current.isPresent()) {
    UserSubscriptions userSubscriptions = current.get();
    long remainingDays = ChronoUnit.DAYS.between(LocalDate.now(), userSubscriptions.getEndDate());
    userSubscriptions.setSubscription(tier);
    userSubscriptions.setEndDate(
        LocalDate.now().plusDays(tier.getDurationInDays() + Math.max(0, remainingDays))
    );
    repository.save(userSubscriptions);
}
```

### Can I grant a premium tier to a user with a cancelled subscription?

**No.** The method only checks for subscriptions with `status = ACTIVE`. If the user has a cancelled or expired subscription, a new subscription will be created instead of upgrading the old one.

### How do I revoke a premium tier?

Use the existing cancellation endpoint:

```bash
curl -X POST "http://localhost:8080/api/v1/subscription?id=<subscription-id>"
```

Or update the subscription status directly:

```sql
UPDATE user_subscriptions
SET status = 'CANCELLED'
WHERE id = '<subscription-id>';
```

### Is this endpoint idempotent?

**Partially.** If the user has an active subscription, calling the endpoint multiple times updates the same record (idempotent). If the user has no active subscription, each call creates a new record (not idempotent).

**Recommendation:** Add a check to prevent duplicate premium grants within a short time window.

---

## Related documentation

- [Subscription Service API Reference](./api/subscription-service.md) *(if exists)*
- [Authentication and Authorization Guide](./security/authentication.md) *(if exists)*
- [Database Schema Documentation](./architecture/database-schema.md) *(if exists)*
- [Stripe Integration Guide](./integrations/stripe.md) *(if exists)*

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-01-15 | Initial documentation for premium tier feature |

---

## Editor's notes

- **BLOCKER:** The `/premium/grant` endpoint has no authentication. This must be fixed before production deployment.
- **BLOCKER:** Hardcoded secrets in `PremiumTierConfig.java` and `application.yml` must be rotated and moved to secure storage.
- **MAJOR:** No audit logging exists for premium tier grants. Add logging to track who granted premium access and when.
- **MAJOR:** The endpoint is not idempotent when creating new subscriptions. Consider adding duplicate detection.
- **MINOR:** Remaining subscription time is not preserved when upgrading. Document this behavior prominently or change the implementation.
