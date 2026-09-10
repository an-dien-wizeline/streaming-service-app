---
title: "Premium Tier Feature — Documentation Set"
description: "Complete documentation for the premium subscription tier grant feature"
audience: [developer, operator, engineer]
doc-type: reference
version: 1.0
last-updated: 2024-01-15
---

# Premium Tier Feature — Documentation Set

This directory contains complete documentation for the premium subscription tier feature, including API reference, configuration, and implementation details.

## Documents in this set

### 1. [Grant Premium Tier Endpoint](./premium-tier-grant-endpoint.md)

**For:** API consumers, integrators  
**Contains:** Endpoint specification, parameters, request/response examples, behavior, error handling

**Key points:**
- POST `/api/subscriptions/premium/grant`
- Upgrades existing subscriptions or creates new ones
- Idempotent operation
- Query parameters: `userId`, `tierId`

### 2. [Premium Tier Configuration Reference](./premium-tier-configuration-reference.md)

**For:** Operators, DevOps, backend engineers  
**Contains:** All configuration properties, Spring bean access, YAML structure, environment variables, security considerations

**Key properties:**
- `premium-tier.enabled` — Feature toggle
- `premium-tier.allowed-sessions` — Max concurrent streams (default: 4)
- `premium-tier.entitlement-signing-key` — Token signing secret
- `premium-tier.partner-billing-api-key` — Billing gateway credentials

### 3. [Premium Tier Service Implementation](./premium-tier-service-implementation.md)

**For:** Backend engineers, code reviewers, maintainers  
**Contains:** Implementation details, flow diagrams, error handling, concurrency considerations, testing strategy

**Key concepts:**
- Two code paths: upgrade existing or create new
- End date always calculated as today + tier duration
- Idempotent by design
- 30 test cases covering happy path, edge cases, errors

## Quick start

### For API consumers

1. Read the [Grant Premium Tier Endpoint](./premium-tier-grant-endpoint.md) document
2. Use the curl example to test the endpoint
3. Handle error responses (see error handling section)

### For operators

1. Read the [Premium Tier Configuration Reference](./premium-tier-configuration-reference.md)
2. Set environment variables or YAML properties for your environment
3. Run configuration tests to verify setup
4. Review security considerations for key rotation

### For developers extending the feature

1. Read the [Premium Tier Service Implementation](./premium-tier-service-implementation.md)
2. Understand the two code paths (upgrade vs. create)
3. Review the test suite to understand expected behavior
4. Consider the future enhancements section for planned work

## Feature overview

The premium tier grant feature allows administrators or authorized services to upgrade users to premium subscriptions. 

**Key behaviors:**

- **Upgrade path**: If user has active subscription → replace with premium tier, extend end date
- **Create path**: If user has no active subscription → create new premium subscription
- **Idempotent**: Multiple calls with same parameters produce same result
- **Non-destructive**: Existing subscriptions are upgraded, not deleted

**Example scenario:**
```
User has: Standard tier, expires 2024-02-15
Action: Grant premium tier (30 days)
Result: Premium tier, expires 2024-02-14 (today + 30 days)
```

## Architecture

```
┌─────────────────────────────────────────────────────┐
│ HTTP Request: POST /api/subscriptions/premium/grant │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ SubscriptionController.grantPremiumTier()           │
│ (validates params, delegates to service)           │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ UserSubscriptionServiceImpl.grantPremiumTier()       │
│ ├─ Fetch tier by ID                                │
│ ├─ Query for active subscription                   │
│ ├─ Upgrade existing OR create new                  │
│ └─ Persist to database                             │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────┐
│ UserSubscriptionRepository                         │
│ (database operations)                              │
└─────────────────────────────────────────────────────┘
```

## Configuration

Minimal configuration needed:

```yaml
premium-tier:
  enabled: true
  allowed-sessions: 4
  entitlement-signing-key: "your-key-here"
  partner-billing-api-key: "your-api-key-here"
```

For production, use environment variables instead of hardcoded values.

## Testing

Each component has comprehensive test coverage:

- **PremiumTierConfigTest** (156 lines) — Configuration bean validation
- **SubscriptionControllerGrantPremiumTierTest** (220 lines) — HTTP endpoint testing
- **UserSubscriptionServiceImplGrantPremiumTierTest** (361 lines) — Service logic testing

Run all tests:

```bash
mvn test -Dtest=*PremiumTier*
```

## Known gaps and open questions

The following items need owner verification before production release:

1. **Authorization model** — Who is authorized to call the grant endpoint? (admin-only, service-to-service, user-initiated?)
2. **Error codes** — What are the specific HTTP status codes for each error scenario?
3. **Rate limiting** — Should this endpoint be rate-limited? At what threshold?
4. **Notifications** — Should users be notified when their tier is upgraded?
5. **Secrets management** — What is the production strategy for key rotation and storage?
6. **Audit logging** — Should all premium grants be logged for compliance?
7. **Concurrency** — Should optimistic locking be implemented to prevent lost updates?

See "Editor's notes" sections in individual documents for details.

## Related services

- **Media Service** — Consumes entitlement tokens to unlock 4K playback
- **Billing Service** — Partner gateway for processing premium upgrades
- **Notification Service** — Sends alerts to users (if implemented)
- **Audit Service** — Logs all premium tier grants (if implemented)

## Support and troubleshooting

### Issue: Endpoint returns 404

**Cause**: Premium tier with given `tierId` does not exist  
**Resolution**: Verify the tier ID exists in the Subscription table

### Issue: Endpoint returns 400

**Cause**: Invalid UUID format for `userId` or `tierId`  
**Resolution**: Ensure both parameters are valid UUIDs

### Issue: User's end date not extending

**Cause**: Idempotent behavior — end date is always recalculated to today + duration  
**Resolution**: This is expected. Multiple calls do not compound; they reset the end date

### Issue: Configuration not loading

**Cause**: Missing `premium-tier` section in `application.yml` or environment variables  
**Resolution**: Add configuration properties or set environment variables (see Configuration Reference)

## Change history

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2024-01-15 | Initial documentation for premium tier grant feature |

## Document metadata

- **Created**: 2024-01-15
- **Last updated**: 2024-01-15
- **Author**: Documentation Engineer
- **Status**: Ready for review
- **Review checklist**: ✅ Accuracy ✅ Completeness ✅ Usability ✅ Consistency ✅ Readability ✅ Structure

---

## Next steps

1. **Verify gaps**: Confirm authorization model, error codes, rate limiting with product owner
2. **Implement missing features**: Audit logging, notifications, concurrency control
3. **Expand documentation**: Add troubleshooting guide, operational runbook, performance tuning
4. **Integrate with other docs**: Link from main API reference, add to architecture guide
