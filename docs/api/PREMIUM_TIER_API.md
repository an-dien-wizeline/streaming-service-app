---
title: "Premium Tier API Reference"
description: "Complete API reference for the premium subscription tier grant endpoint"
audience: developer
doc-type: reference
version: 1.0
last-updated: 2025-01-15
---

# Premium tier API reference

## POST /api/v1/subscription/premium/grant

Grants a premium subscription tier to a user, either by upgrading an existing active subscription or creating a new premium subscription.

### Authentication

⚠️ **CRITICAL SECURITY ISSUE:** This endpoint currently has **NO authentication or authorization**.

**Current state:** Any client with network access can call this endpoint.

**Required fix:** Add role-based access control:

```java
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
```

**Expected behavior after fix:** Only authenticated users with the `ROLE_ADMIN` authority can call this endpoint.

### Base URL

```
http://localhost:8080/api/v1/subscription
```

In production, replace with your actual API gateway or service URL.

### Endpoint

```
POST /premium/grant
```

### Request

#### Headers

| Header | Value | Required | Description |
|--------|-------|----------|-------------|
| `Content-Type` | `application/x-www-form-urlencoded` | No | Default for query parameters |

**Note:** Once authentication is added, you will need:

| Header | Value | Required | Description |
|--------|-------|----------|-------------|
| `Authorization` | `Bearer <JWT_TOKEN>` | Yes | JWT token with `ROLE_ADMIN` authority |

#### Parameters

| Parameter | Type | Location | Required | Constraints | Description |
|-----------|------|----------|----------|-------------|-------------|
| `userId` | UUID | Query | Yes | Valid UUID v4 format | The unique identifier of the user receiving the premium tier |
| `tierId` | UUID | Query | Yes | Valid UUID v4 format; must exist in `subscriptions` table | The unique identifier of the premium subscription tier to grant |

#### Example values

```
userId: a1b2c3d4-e5f6-7890-abcd-ef1234567890
tierId: f1e2d3c4-b5a6-7890-cdef-123456789abc
```

### Response

#### Success (200 OK)

**Status code:** `200`

**Headers:**
```
Content-Length: 0
```

**Body:** Empty (no content)

**Meaning:** The premium tier was successfully granted. Check the database for the updated or created `user_subscriptions` record.

#### Error responses

##### 400 Bad Request — Invalid UUID format

**Condition:** `userId` or `tierId` is not a valid UUID.

**Example request:**
```bash
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=invalid&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc"
```

**Response:**
```json
{
  "timestamp": "2025-01-15T14:30:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Failed to convert value of type 'java.lang.String' to required type 'java.util.UUID'; Invalid UUID string: invalid",
  "path": "/api/v1/subscription/premium/grant"
}
```

**Resolution:** Provide valid UUID v4 strings for both parameters.

##### 404 Not Found — Tier does not exist

**Condition:** The `tierId` does not exist in the `subscriptions` table.

**Example request:**
```bash
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=00000000-0000-0000-0000-000000000000"
```

**Response:**
```json
{
  "timestamp": "2025-01-15T14:30:00.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "Subscription tier not found",
  "path": "/api/v1/subscription/premium/grant"
}
```

**Resolution:** Verify the tier ID exists by querying:
```sql
SELECT id, name FROM subscriptions WHERE id = '<tierId>';
```

##### 500 Internal Server Error — Database or system error

**Condition:** Database connection failure, constraint violation, or unexpected exception.

**Example response:**
```json
{
  "timestamp": "2025-01-15T14:30:00.123Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Could not execute statement; SQL [n/a]; constraint [unique_order_id]",
  "path": "/api/v1/subscription/premium/grant"
}
```

**Resolution:** Check application logs for the full stack trace. Common causes:
- Database connection pool exhausted
- Duplicate `orderId` constraint violation (rare, due to UUID generation)
- Transaction timeout

### Examples

#### Example 1: Grant premium to a new user (curl)

```bash
curl -X POST \
  "http://localhost:8080/api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc" \
  -v
```

**Response:**
```
< HTTP/1.1 200 OK
< Content-Length: 0
```

#### Example 2: Grant premium to a new user (Java RestTemplate)

```java
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

public class PremiumTierClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = "http://localhost:8080/api/v1/subscription";

    public void grantPremiumTier(UUID userId, UUID tierId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/premium/grant")
            .queryParam("userId", userId.toString())
            .queryParam("tierId", tierId.toString())
            .toUriString();

        ResponseEntity<Void> response = restTemplate.postForEntity(url, null, Void.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            System.out.println("Premium tier granted successfully");
        }
    }
}
```

#### Example 3: Grant premium with error handling (Java)

```java
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

public class PremiumTierClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = "http://localhost:8080/api/v1/subscription";

    public boolean grantPremiumTier(UUID userId, UUID tierId) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/premium/grant")
                .queryParam("userId", userId.toString())
                .queryParam("tierId", tierId.toString())
                .toUriString();

            restTemplate.postForEntity(url, null, Void.class);
            return true;

        } catch (HttpClientErrorException.NotFound e) {
            System.err.println("Tier not found: " + e.getResponseBodyAsString());
            return false;

        } catch (HttpClientErrorException.BadRequest e) {
            System.err.println("Invalid parameters: " + e.getResponseBodyAsString());
            return false;

        } catch (HttpServerErrorException e) {
            System.err.println("Server error: " + e.getResponseBodyAsString());
            return false;

        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            return false;
        }
    }
}
```

#### Example 4: Grant premium to a user with an existing subscription (Python)

```python
import requests
import uuid

def grant_premium_tier(user_id: uuid.UUID, tier_id: uuid.UUID) -> bool:
    """
    Grants a premium subscription tier to a user.
    
    Args:
        user_id: UUID of the user
        tier_id: UUID of the premium tier
        
    Returns:
        True if successful, False otherwise
    """
    url = "http://localhost:8080/api/v1/subscription/premium/grant"
    params = {
        "userId": str(user_id),
        "tierId": str(tier_id)
    }
    
    try:
        response = requests.post(url, params=params)
        response.raise_for_status()
        print(f"Premium tier granted to user {user_id}")
        return True
        
    except requests.exceptions.HTTPError as e:
        if e.response.status_code == 404:
            print(f"Tier {tier_id} not found")
        elif e.response.status_code == 400:
            print(f"Invalid UUID format")
        else:
            print(f"HTTP error: {e.response.status_code}")
        return False
        
    except Exception as e:
        print(f"Unexpected error: {str(e)}")
        return False

# Usage
user_id = uuid.UUID("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
tier_id = uuid.UUID("f1e2d3c4-b5a6-7890-cdef-123456789abc")
grant_premium_tier(user_id, tier_id)
```

#### Example 5: Grant premium with authentication header (once security is fixed)

```bash
# Obtain JWT token first
TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.token')

# Grant premium tier with authentication
curl -X POST \
  "http://localhost:8080/api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc" \
  -H "Authorization: Bearer $TOKEN"
```

### Behavior details

#### Scenario 1: User has an active subscription

The endpoint **upgrades** the existing subscription:

**Before:**
```sql
SELECT * FROM user_subscriptions WHERE user_id = 'a1b2c3d4-...' AND status = 'ACTIVE';
```
```
id          | user_id      | subscription_id | start_date | end_date   | status
------------|--------------|-----------------|------------|------------|--------
sub-123     | a1b2c3d4-... | tier-basic      | 2025-01-01 | 2025-01-31 | ACTIVE
```

**After calling `/premium/grant` with `tierId=tier-premium` (90-day duration):**
```sql
SELECT * FROM user_subscriptions WHERE user_id = 'a1b2c3d4-...' AND status = 'ACTIVE';
```
```
id          | user_id      | subscription_id | start_date | end_date   | status
------------|--------------|-----------------|------------|------------|--------
sub-123     | a1b2c3d4-... | tier-premium    | 2025-01-01 | 2025-04-15 | ACTIVE
```

**Key observations:**
- Same `id` (record updated, not replaced)
- `subscription_id` changed from `tier-basic` to `tier-premium`
- `end_date` recalculated as `today + 90 days` (remaining time on old subscription is **not preserved**)
- `start_date` unchanged

#### Scenario 2: User has no active subscription

The endpoint **creates** a new subscription:

**Before:**
```sql
SELECT * FROM user_subscriptions WHERE user_id = 'a1b2c3d4-...' AND status = 'ACTIVE';
```
```
(0 rows)
```

**After calling `/premium/grant`:**
```sql
SELECT * FROM user_subscriptions WHERE user_id = 'a1b2c3d4-...' AND status = 'ACTIVE';
```
```
id                  | user_id      | order_id            | subscription_id | start_date | end_date   | status
--------------------|--------------|---------------------|-----------------|------------|------------|--------
<generated-uuid>    | a1b2c3d4-... | <generated-uuid>    | tier-premium    | 2025-01-15 | 2025-04-15 | ACTIVE
```

**Key observations:**
- New record created with generated `id`
- `order_id` is a **random UUID** (not tied to an actual order or payment)
- `start_date` is today
- `end_date` is `today + tier.durationInDays`

### Implementation notes

#### Source code location

```
services/subscription-service/src/main/java/io/github/marianciuc/streamingservice/subscription/controller/SubscriptionController.java
```

**Method:**
```java
@PostMapping("/premium/grant")
public ResponseEntity<Void> grantPremiumTier(
    @RequestParam("userId") UUID userId,
    @RequestParam("tierId") UUID tierId
) {
    userSubscriptionService.grantPremiumTier(userId, tierId);
    return ResponseEntity.ok().build();
}
```

#### Service implementation

```
services/subscription-service/src/main/java/io/github/marianciuc/streamingservice/subscription/service/impl/UserSubscriptionServiceImpl.java
```

**Method:**
```java
@Override
public void grantPremiumTier(UUID userId, UUID tierId) {
    Subscription tier = subscriptionService.getSubscription(tierId);
    Optional<UserSubscriptions> current = repository.findByUserIdAndStatus(
        userId, 
        SubscriptionStatus.ACTIVE
    );

    if (current.isPresent()) {
        UserSubscriptions userSubscriptions = current.get();
        userSubscriptions.setSubscription(tier);
        userSubscriptions.setEndDate(
            LocalDate.now().plusDays(tier.getDurationInDays())
        );
        repository.save(userSubscriptions);
    } else {
        repository.save(
            createUserSubscription(tier, userId, UUID.randomUUID())
        );
    }
}
```

### Rate limits

**Current state:** No rate limiting is implemented.

**Recommendation:** Implement rate limiting to prevent abuse:
- **Per admin user:** 100 grants per hour
- **Global:** 1,000 grants per hour

### Idempotency

**Partially idempotent:**

- **If user has an active subscription:** Calling the endpoint multiple times with the same parameters updates the same record (idempotent)
- **If user has no active subscription:** Each call creates a new record with a different `id` and `order_id` (not idempotent)

**Recommendation:** Add a check to prevent duplicate grants:

```java
// Check if a premium grant was made in the last 5 minutes
Optional<UserSubscriptions> recentGrant = repository.findRecentGrant(userId, tierId, LocalDateTime.now().minusMinutes(5));
if (recentGrant.isPresent()) {
    throw new DuplicateGrantException("Premium tier already granted to this user recently");
}
```

### Versioning

**Current version:** `v1`

**Endpoint path:** `/api/v1/subscription/premium/grant`

**Backward compatibility:** If the endpoint contract changes in the future, introduce a new version (`/api/v2/subscription/premium/grant`) and deprecate the old version.

---

## Related endpoints

### Get active subscription

**Endpoint:** `GET /api/v1/subscription/active`

**Description:** Retrieves the active subscription for the authenticated user.

**Authentication:** Required (JWT token)

**Example:**
```bash
curl -X GET "http://localhost:8080/api/v1/subscription/active" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### Cancel subscription

**Endpoint:** `POST /api/v1/subscription`

**Description:** Cancels an active subscription.

**Authentication:** Required (JWT token)

**Example:**
```bash
curl -X POST "http://localhost:8080/api/v1/subscription?id=<subscription-id>" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-01-15 | Initial API reference for premium tier grant endpoint |

---

## Support

For questions or issues with this API:

- **Internal team:** Contact the subscription-service team via Slack (#subscription-service)
- **External developers:** Email api-support@example.com
- **Security issues:** Email security@example.com immediately

---

## See also

- [Premium Tier Feature Overview](../PREMIUM_TIER.md)
- [Premium Tier Security Advisory](./PREMIUM_TIER_SECURITY_ADVISORY.md)
- [Configuration Guide](../PREMIUM_TIER.md#configuration)
