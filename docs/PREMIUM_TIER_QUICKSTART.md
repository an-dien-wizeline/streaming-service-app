---
title: "Premium Tier Quick Start Guide"
description: "Quick reference for granting premium subscriptions to users"
audience: [developer, admin]
doc-type: how-to
version: 1.0
last-updated: 2025-01-15
---

# Premium tier quick start guide

This guide shows you how to grant premium subscription tiers to users using the subscription service API.

## ⚠️ Before you begin

**SECURITY WARNING:** The premium grant endpoint currently has NO authentication. This guide assumes you are working in a development or staging environment. **DO NOT use this in production** until the security issues are fixed. See the [Security Advisory](./api/PREMIUM_TIER_SECURITY_ADVISORY.md) for details.

## Prerequisites

- Subscription service running (default: `http://localhost:8080`)
- PostgreSQL database accessible
- At least one premium tier defined in the `subscriptions` table
- User ID (UUID) of the user receiving the premium tier

## Step 1: Verify the premium tier exists

Check that a premium tier is defined in your database:

```sql
SELECT id, name, description, duration_in_days, price, allowed_active_sessions
FROM subscriptions
WHERE name ILIKE '%premium%';
```

**Example result:**
```
id                                   | name    | duration_in_days | price  | allowed_active_sessions
-------------------------------------|---------|------------------|--------|------------------------
f1e2d3c4-b5a6-7890-cdef-123456789abc | Premium | 90               | 19.99  | 4
```

If no premium tier exists, create one:

```sql
INSERT INTO subscriptions (
    id, name, description, duration_in_days, price, currency,
    allowed_active_sessions, record_status, is_temporary, created_at, updated_at
)
VALUES (
    gen_random_uuid(),
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

## Step 2: Grant premium tier to a user

Use the `/premium/grant` endpoint:

```bash
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=<USER_ID>&tierId=<TIER_ID>"
```

**Example:**
```bash
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc" \
  -v
```

**Expected response:**
```
HTTP/1.1 200 OK
Content-Length: 0
```

## Step 3: Verify the grant

Check that the user now has an active premium subscription:

```sql
SELECT us.id, us.user_id, s.name AS tier_name, us.start_date, us.end_date, us.status
FROM user_subscriptions us
JOIN subscriptions s ON us.subscription_id = s.id
WHERE us.user_id = '<USER_ID>' AND us.status = 'ACTIVE';
```

**Example result:**
```
id                                   | user_id                              | tier_name | start_date | end_date   | status
-------------------------------------|--------------------------------------|-----------|------------|------------|--------
12345678-1234-5678-abcd-123456789012 | a1b2c3d4-e5f6-7890-abcd-ef1234567890 | Premium   | 2025-01-15 | 2025-04-15 | ACTIVE
```

## Common scenarios

### Scenario 1: User has no subscription

When you grant a premium tier to a user with no active subscription:

- A new `user_subscriptions` record is created
- `start_date` is set to today
- `end_date` is set to `today + tier.durationInDays`
- `status` is `ACTIVE`
- `order_id` is a generated UUID (not tied to a payment)

### Scenario 2: User has an active basic subscription

When you grant a premium tier to a user with an existing active subscription:

- The existing `user_subscriptions` record is updated (not replaced)
- `subscription_id` is changed to the premium tier
- `end_date` is recalculated as `today + tier.durationInDays`
- **The remaining time on the old subscription is NOT preserved**

**Example:**
- User has 20 days left on Basic tier
- You grant Premium (90 days)
- New end date: today + 90 days (the 20 days are lost)

### Scenario 3: User has a cancelled or expired subscription

When you grant a premium tier to a user with a cancelled or expired subscription:

- A **new** `user_subscriptions` record is created (the old one is not reactivated)
- The old subscription remains in the database with `status = CANCELLED` or `EXPIRED`

## Troubleshooting

### Error: 400 Bad Request - Invalid UUID

**Problem:** The `userId` or `tierId` is not a valid UUID.

**Solution:** Verify the format. UUIDs must be in the format `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`.

**Example:**
```bash
# ❌ Invalid
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=123&tierId=abc"

# ✅ Valid
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc"
```

### Error: 404 Not Found - Subscription tier not found

**Problem:** The `tierId` does not exist in the `subscriptions` table.

**Solution:** Query the database to find valid tier IDs:

```sql
SELECT id, name FROM subscriptions WHERE record_status = 'ACTIVE';
```

### Error: 500 Internal Server Error

**Problem:** Database connection failure or unexpected error.

**Solution:** Check the application logs:

```bash
# Docker
docker logs subscription-service

# Kubernetes
kubectl logs deployment/subscription-service
```

Common causes:
- Database connection pool exhausted
- PostgreSQL service is down
- Network connectivity issues

## Using the API from code

### Java (RestTemplate)

```java
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.UUID;

public void grantPremiumTier(UUID userId, UUID tierId) {
    RestTemplate restTemplate = new RestTemplate();
    String url = UriComponentsBuilder
        .fromHttpUrl("http://localhost:8080/api/v1/subscription/premium/grant")
        .queryParam("userId", userId.toString())
        .queryParam("tierId", tierId.toString())
        .toUriString();

    restTemplate.postForEntity(url, null, Void.class);
    System.out.println("Premium tier granted to user " + userId);
}
```

### Python (requests)

```python
import requests
import uuid

def grant_premium_tier(user_id: uuid.UUID, tier_id: uuid.UUID):
    url = "http://localhost:8080/api/v1/subscription/premium/grant"
    params = {
        "userId": str(user_id),
        "tierId": str(tier_id)
    }
    
    response = requests.post(url, params=params)
    response.raise_for_status()
    print(f"Premium tier granted to user {user_id}")

# Usage
grant_premium_tier(
    user_id=uuid.UUID("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
    tier_id=uuid.UUID("f1e2d3c4-b5a6-7890-cdef-123456789abc")
)
```

### JavaScript (fetch)

```javascript
async function grantPremiumTier(userId, tierId) {
    const url = `http://localhost:8080/api/v1/subscription/premium/grant?userId=${userId}&tierId=${tierId}`;
    
    const response = await fetch(url, {
        method: 'POST'
    });
    
    if (!response.ok) {
        throw new Error(`Failed to grant premium: ${response.status}`);
    }
    
    console.log(`Premium tier granted to user ${userId}`);
}

// Usage
grantPremiumTier(
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'f1e2d3c4-b5a6-7890-cdef-123456789abc'
);
```

## Next steps

- Read the [full feature documentation](../PREMIUM_TIER.md) for detailed information
- Review the [Security Advisory](./api/PREMIUM_TIER_SECURITY_ADVISORY.md) before deploying to production
- Check the [API Reference](./api/PREMIUM_TIER_API.md) for complete endpoint documentation

## Getting help

- **Internal team:** Contact the subscription-service team via Slack (#subscription-service)
- **Issues:** Create a ticket in Jira (project: SUBSCRIPTION)
- **Urgent:** Page the on-call engineer via PagerDuty

---

**Last updated:** 2025-01-15  
**Version:** 1.0
