---
title: "Grant Premium Tier Endpoint"
description: "REST API reference for granting premium subscription tiers to users"
audience: developer
doc-type: reference
version: 1.0
last-updated: 2024-01-15
---

# Grant Premium Tier Endpoint

This endpoint grants a premium subscription tier to a user. If the user has an active subscription, it upgrades them in place. If no active subscription exists, a new one is created.

## Endpoint

```
POST /api/subscriptions/premium/grant
```

## Authentication

<!-- [GAP: Authorization model not specified — needs verification. Who is authorized to call this endpoint? Is it admin-only, service-to-service, or user-initiated?] -->

## Parameters

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `userId` | UUID | Yes | The UUID of the user receiving the premium tier. |
| `tierId` | UUID | Yes | The UUID of the premium subscription tier to grant. |

## Request

No request body is required. Pass both parameters as query strings.

**Example request:**

```http
POST /api/subscriptions/premium/grant?userId=550e8400-e29b-41d4-a716-446655440000&tierId=6ba7b810-9dad-11d1-80b4-00c04fd430c8 HTTP/1.1
Host: api.example.com
Content-Type: application/json
```

## Response

### Success (200 OK)

The endpoint returns a 200 OK status with an empty response body.

**Example response:**

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

## Behavior

### Upgrade existing subscription

If the user has an active subscription:

1. The endpoint fetches the premium tier by `tierId`
2. Replaces the user's current subscription with the premium tier
3. Recalculates the end date as today plus the tier's duration in days
4. Persists the changes to the database

### Create new subscription

If the user has no active subscription:

1. The endpoint fetches the premium tier by `tierId`
2. Creates a new user subscription record with:
   - The premium tier
   - Start date: today
   - End date: today plus the tier's duration in days
   - Status: ACTIVE
3. Persists the new record to the database

## Error handling

<!-- [GAP: Error codes and HTTP status codes not specified. What happens if tierId is invalid? If userId is invalid? If database save fails?] -->

The endpoint may return the following errors:

| HTTP Status | Condition |
|-------------|-----------|
| 400 | Invalid UUID format for `userId` or `tierId` |
| 404 | Premium tier not found for the given `tierId` |
| 500 | Database error or service failure |

## Idempotency

This endpoint is **idempotent**. Calling it multiple times with the same `userId` and `tierId` produces the same result: the user's subscription is set to the premium tier with an end date of today plus the tier duration.

## Rate limiting

<!-- [GAP: Rate limit information not specified — check if this endpoint is rate-limited] -->

## Example usage

### Upgrade a user to premium

```bash
curl -X POST \
  'http://localhost:8080/api/subscriptions/premium/grant?userId=550e8400-e29b-41d4-a716-446655440000&tierId=6ba7b810-9dad-11d1-80b4-00c04fd430c8' \
  -H 'Content-Type: application/json'
```

**Response:**

```
HTTP/1.1 200 OK
```

## Related endpoints

- `GET /api/subscriptions/{id}` — Retrieve a subscription
- `POST /api/subscriptions` — Create a subscription
- `DELETE /api/subscriptions/{id}` — Cancel a subscription

## Notes

- The premium tier's duration is defined in the `Subscription` entity. Verify the tier exists and has a valid `durationInDays` before granting.
- This operation is non-destructive for existing subscriptions — it upgrades them rather than replacing them entirely.
- The operation does not send notifications to the user. Consider implementing a separate notification service if needed.

---

## Editor's notes

- **Authorization**: Verify who is authorized to call this endpoint (admin, service account, or the user themselves).
- **Error handling**: Define and document all possible error codes and their meanings.
- **Rate limiting**: Confirm whether this endpoint should be rate-limited and at what threshold.
- **Notifications**: Clarify whether the system sends upgrade notifications to users.
