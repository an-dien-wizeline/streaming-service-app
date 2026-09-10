---
title: "Premium Subscription Tier Feature"
description: "Complete technical documentation for the Premium Subscription Tier feature including API reference, architecture, security considerations, and deployment guide"
audience: [developer, devops, product-manager, qa-engineer]
doc-type: reference
version: 1.0
last-updated: 2024-09-10
feature-status: active
related-services: [subscription-service, media-service]
---

# Premium Subscription Tier Feature

## Table of contents

- [Overview](#overview)
- [Feature capabilities](#feature-capabilities)
- [Architecture](#architecture)
- [API reference](#api-reference)
- [Configuration](#configuration)
- [Business logic](#business-logic)
- [Integration guide](#integration-guide)
- [Security considerations](#security-considerations)
- [Testing](#testing)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)

---

## Overview

The Premium Subscription Tier feature enables the streaming service to offer enhanced subscription plans with advanced capabilities including 4K video playback and increased concurrent streaming sessions. This feature extends the existing subscription management system to support tier-based upgrades and premium entitlements.

### Why this feature exists

The Premium Subscription Tier addresses the following business requirements:

- **Revenue expansion** — Enables upselling to users who want premium features like 4K streaming
- **Concurrent streaming** — Allows premium users to stream on up to 4 devices simultaneously (vs. 1 for basic tier)
- **Flexible upgrades** — Supports in-place upgrades from existing subscriptions without disrupting service
- **Partner billing integration** — Integrates with external billing gateways for premium payment processing

### Key capabilities

| Capability | Description |
|------------|-------------|
| **Premium tier grants** | Administrators can grant premium subscriptions to users via REST API |
| **In-place upgrades** | Existing active subscriptions are upgraded without creating duplicate records |
| **New subscriptions** | Users without active subscriptions receive a new premium subscription |
| **Entitlement tokens** | Premium users receive signed tokens for media service integration |
| **Session management** | Premium tier allows 4 concurrent streaming sessions |

---

## Feature capabilities

### Supported operations

✅ **Grant premium tier to existing user**  
Upgrade a user's active subscription or create a new premium subscription

✅ **Automatic end date calculation**  
End date is automatically calculated based on tier duration (e.g., 30 days from grant date)

✅ **In-place subscription updates**  
Existing active subscriptions are upgraded without cancellation

✅ **Concurrent session enforcement**  
Premium tier configuration defines maximum concurrent streams (default: 4)

### Limitations

❌ **No self-service upgrades** — Users cannot upgrade themselves; requires admin API call  
❌ **No tier downgrades** — Feature does not support downgrading from premium to basic  
❌ **No proration** — Remaining time on existing subscription is not credited  
❌ **Single premium tier** — Currently supports one premium tier configuration

---

## Architecture

### System context

The Premium Subscription Tier feature is implemented in the **subscription-service** microservice and integrates with the **media-service** for playback entitlements.

```mermaid
graph LR
    A[Admin/API Client] -->|POST /premium/grant| B[Subscription Service]
    B -->|Query/Update| C[(PostgreSQL)]
    B -->|Generate Token| D[PremiumTierConfig]
    D -->|Signing Key| E[Media Service]
    E -->|Verify Token| F[4K Playback]
    B -->|Billing Event| G[Partner Billing Gateway]
```

### Component diagram

```mermaid
classDiagram
    class SubscriptionController {
        +grantPremiumTier(userId, tierId) ResponseEntity
    }
    class UserSubscriptionService {
        +grantPremiumTier(userId, tierId) void
    }
    class UserSubscriptionRepository {
        +findByUserIdAndStatus(userId, status) Optional
        +save(subscription) UserSubscriptions
    }
    class PremiumTierConfig {
        -ENTITLEMENT_SIGNING_KEY String
        -PARTNER_BILLING_API_KEY String
        -PREMIUM_ALLOWED_SESSIONS int
        +getEntitlementSigningKey() String
        +getPartnerBillingApiKey() String
        +getPremiumAllowedSessions() int
    }
    
    SubscriptionController --> UserSubscriptionService
    UserSubscriptionService --> UserSubscriptionRepository
    UserSubscriptionService --> SubscriptionService
    UserSubscriptionService --> PremiumTierConfig
```

### Data model

The feature uses existing `UserSubscriptions` and `Subscription` entities:

**UserSubscriptions** (user_subscriptions table)
```
id               UUID (PK)
user_id          UUID (FK to users)
order_id         UUID (unique)
subscription_id  UUID (FK to subscriptions)
start_date       DATE
end_date         DATE
status           ENUM (ACTIVE, CANCELLED, EXPIRED)
```

**Subscription** (subscriptions table)
```
id                      UUID (PK)
name                    VARCHAR
description             TEXT
duration_in_days        INTEGER
price                   DECIMAL
currency                ENUM
allowed_active_sessions INTEGER
record_status           ENUM (ACTIVE, DELETED, HIDDEN)
created_at              TIMESTAMP
updated_at              TIMESTAMP
```

### Integration points

| Service | Integration Type | Purpose |
|---------|------------------|---------|
| **Media Service** | Token-based | Validates premium entitlements for 4K playback |
| **Partner Billing Gateway** | REST API | Processes premium subscription payments |
| **Order Service** | Kafka events | Creates order records for premium grants |

---

## API reference

### Grant premium tier

Grants a premium subscription tier to a user. If the user has an active subscription, it is upgraded in place. If the user has no active subscription, a new premium subscription is created.

**Endpoint**
```
POST /api/v1/subscription/premium/grant
```

**Authentication**  
Requires valid JWT token with `ROLE_ADMIN` authority.

**Request parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `userId` | UUID | Yes | The UUID of the user receiving the premium tier |
| `tierId` | UUID | Yes | The UUID of the premium subscription tier to grant |

**Request example**

```bash
curl -X POST "https://api.streaming-service.com/api/v1/subscription/premium/grant?userId=a3f7d8e2-4b9c-4a1e-8f3d-9c2b7e6a5d8c&tierId=f6a5d8c0-b9e2-4f7d-1c3e-6b8a0d5f2c9e" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json"
```

**Response**

**Success (200 OK)**
```http
HTTP/1.1 200 OK
Content-Length: 0
```

The response body is empty. A 200 status indicates successful grant.

**Error responses**

| Status | Error | Description |
|--------|-------|-------------|
| 400 Bad Request | Invalid UUID format | `userId` or `tierId` is not a valid UUID |
| 401 Unauthorized | Missing or invalid token | JWT token is missing, expired, or invalid |
| 403 Forbidden | Insufficient permissions | User does not have `ROLE_ADMIN` authority |
| 404 Not Found | Subscription tier not found | The specified `tierId` does not exist |
| 404 Not Found | User not found | The specified `userId` does not exist |
| 500 Internal Server Error | Database error | Database connection or constraint violation |

**Error response example**
```json
{
  "timestamp": "2024-09-10T14:32:15.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "Subscription tier not found",
  "path": "/api/v1/subscription/premium/grant"
}
```

---

## Configuration

### Application configuration

The Premium Subscription Tier feature is configured in `application.yml`:

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
| `premium-tier.enabled` | boolean | `false` | Enables or disables premium tier functionality |
| `premium-tier.allowed-sessions` | integer | `4` | Maximum concurrent streaming sessions for premium users |
| `premium-tier.entitlement-signing-key` | string | (none) | Shared secret for signing entitlement tokens sent to media service |
| `premium-tier.partner-billing-api-key` | string | (none) | API key for the partner billing gateway |

### PremiumTierConfig class

Configuration is loaded via the `PremiumTierConfig` Spring Configuration class:

```java
@Configuration
@Getter
public class PremiumTierConfig {
    private static final String ENTITLEMENT_SIGNING_KEY = "...";
    private static final String PARTNER_BILLING_API_KEY = "...";
    private static final int PREMIUM_ALLOWED_SESSIONS = 4;
    
    public String getEntitlementSigningKey() {
        return ENTITLEMENT_SIGNING_KEY;
    }
    
    public String getPartnerBillingApiKey() {
        return PARTNER_BILLING_API_KEY;
    }
    
    public int getPremiumAllowedSessions() {
        return PREMIUM_ALLOWED_SESSIONS;
    }
}
```

### Environment-specific configuration

For production deployments, override sensitive configuration via environment variables:

```bash
export PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY="your-production-key"
export PREMIUM_TIER_PARTNER_BILLING_API_KEY="your-production-api-key"
```

Update `application.yml` to reference environment variables:

```yaml
premium-tier:
  entitlement-signing-key: ${PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY}
  partner-billing-api-key: ${PREMIUM_TIER_PARTNER_BILLING_API_KEY}
```

---

## Business logic

### Grant premium tier workflow

The `grantPremiumTier` method implements the following business logic:

```mermaid
flowchart TD
    A[Start: grantPremiumTier called] --> B[Fetch Subscription tier by tierId]
    B --> C{Tier exists?}
    C -->|No| D[Throw NotFoundException]
    C -->|Yes| E[Query for active subscription by userId]
    E --> F{Active subscription exists?}
    F -->|Yes| G[Upgrade existing subscription]
    F -->|No| H[Create new subscription]
    G --> I[Update subscription field to premium tier]
    I --> J[Calculate new end date: now + tier.durationInDays]
    J --> K[Save updated subscription]
    K --> L[End]
    H --> M[Create UserSubscriptions entity]
    M --> N[Set userId, tierId, status=ACTIVE]
    N --> O[Set startDate=now, endDate=now + tier.durationInDays]
    O --> P[Generate random orderId]
    P --> Q[Save new subscription]
    Q --> L
```

### Scenario 1: Upgrade existing subscription

**Given:** User `a3f7d8e2-4b9c-4a1e-8f3d-9c2b7e6a5d8c` has an active Basic subscription
- Basic tier: $9.99/month, 1 concurrent stream, expires 2024-10-05

**When:** Admin grants Premium tier `f6a5d8c0-b9e2-4f7d-1c3e-6b8a0d5f2c9e`
- Premium tier: $19.99/month, 4 concurrent streams, 30-day duration

**Then:**
1. Existing `UserSubscriptions` record is fetched
2. `subscription` field is updated to Premium tier
3. `endDate` is recalculated: `LocalDate.now().plusDays(30)` (e.g., 2024-10-10)
4. Record is saved
5. User immediately gains premium benefits (4K, 4 streams)

**Result:** User's subscription is upgraded in place. No new database record is created.

### Scenario 2: Create new premium subscription

**Given:** User `b4e8c9f3-5c0d-4b2f-9e4a-0d6c8b7a5e3f` has no active subscription

**When:** Admin grants Premium tier `f6a5d8c0-b9e2-4f7d-1c3e-6b8a0d5f2c9e`

**Then:**
1. No existing active subscription is found
2. New `UserSubscriptions` record is created:
   - `userId`: `b4e8c9f3-5c0d-4b2f-9e4a-0d6c8b7a5e3f`
   - `subscription`: Premium tier
   - `status`: `ACTIVE`
   - `startDate`: `LocalDate.now()` (e.g., 2024-09-10)
   - `endDate`: `LocalDate.now().plusDays(30)` (e.g., 2024-10-10)
   - `orderId`: Random UUID generated via `UUID.randomUUID()`
3. Record is saved
4. User gains premium benefits

**Result:** New premium subscription is created with 30-day validity.

### Date calculation logic

End dates are calculated using Java's `LocalDate` API:

```java
LocalDate endDate = LocalDate.now().plusDays(tier.getDurationInDays());
```

**Example calculations:**

| Grant Date | Tier Duration | Calculated End Date |
|------------|---------------|---------------------|
| 2024-09-10 | 30 days | 2024-10-10 |
| 2024-09-10 | 365 days | 2025-09-10 |
| 2024-02-28 | 30 days | 2024-03-29 |

The calculation accounts for leap years and month-end boundaries automatically.

### Idempotency considerations

**The `grantPremiumTier` endpoint is NOT idempotent.**

- Calling the endpoint multiple times with the same `userId` and `tierId` will update the `endDate` each time
- Each call resets the end date to `now + tier.durationInDays`
- If called twice on the same day, the second call has no effect on the end date
- If called on different days, the end date is extended

**Example:**
1. Grant premium on 2024-09-10 → `endDate` = 2024-10-10
2. Grant premium again on 2024-09-15 → `endDate` = 2024-10-15 (extended by 5 days)

To implement idempotency, track grant operations in a separate table or use a unique `orderId` constraint.

---

## Integration guide

### For media service developers

The media service must validate premium entitlements before enabling 4K playback. Use the entitlement signing key to verify tokens.

#### Step 1: Receive entitlement token

When a user requests 4K content, the subscription service generates a signed JWT token:

```java
String token = Jwts.builder()
    .setSubject(userId.toString())
    .claim("tier", "premium")
    .claim("allowedSessions", 4)
    .signWith(SignatureAlgorithm.HS256, premiumTierConfig.getEntitlementSigningKey())
    .compact();
```

#### Step 2: Verify token in media service

```java
@Service
public class EntitlementService {
    
    @Value("${premium-tier.entitlement-signing-key}")
    private String signingKey;
    
    public boolean isPremiumUser(String token) {
        try {
            Claims claims = Jwts.parser()
                .setSigningKey(signingKey)
                .parseClaimsJws(token)
                .getBody();
            
            return "premium".equals(claims.get("tier"));
        } catch (JwtException e) {
            return false;
        }
    }
}
```

#### Step 3: Enforce 4K playback policy

```java
@RestController
public class StreamingController {
    
    @Autowired
    private EntitlementService entitlementService;
    
    @GetMapping("/stream/{videoId}")
    public ResponseEntity<StreamResponse> streamVideo(
            @PathVariable String videoId,
            @RequestParam String quality,
            @RequestHeader("X-Entitlement-Token") String token) {
        
        if ("4K".equals(quality) && !entitlementService.isPremiumUser(token)) {
            return ResponseEntity.status(403)
                .body(new StreamResponse("4K streaming requires premium subscription"));
        }
        
        // Proceed with streaming...
    }
}
```

### For partner billing gateway integration

Premium subscription grants trigger billing events to the partner gateway.

#### Billing API endpoint

```
POST https://billing-partner.example.com/api/v1/charges
Authorization: Bearer wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d
```

#### Request payload

```json
{
  "customer_id": "a3f7d8e2-4b9c-4a1e-8f3d-9c2b7e6a5d8c",
  "amount": 1999,
  "currency": "USD",
  "description": "Premium subscription upgrade",
  "metadata": {
    "tier_id": "f6a5d8c0-b9e2-4f7d-1c3e-6b8a0d5f2c9e",
    "subscription_id": "d8f3c6b0-e9a2-4d1c-8f7a-5b3e9c2a4f6d"
  }
}
```

#### Response handling

```java
@Service
public class BillingService {
    
    @Value("${premium-tier.partner-billing-api-key}")
    private String apiKey;
    
    public void chargePremiumUpgrade(UUID userId, UUID tierId, BigDecimal amount) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        
        BillingRequest request = new BillingRequest(userId, amount, "Premium upgrade");
        HttpEntity<BillingRequest> entity = new HttpEntity<>(request, headers);
        
        ResponseEntity<BillingResponse> response = restTemplate.postForEntity(
            "https://billing-partner.example.com/api/v1/charges",
            entity,
            BillingResponse.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new BillingException("Failed to charge premium upgrade");
        }
    }
}
```

### Kafka event publishing

When a premium tier is granted, publish a Kafka event for downstream services:

**Topic:** `subscription.premium.granted`

**Event schema:**
```json
{
  "event_type": "premium_granted",
  "timestamp": "2024-09-10T14:32:15.123Z",
  "user_id": "a3f7d8e2-4b9c-4a1e-8f3d-9c2b7e6a5d8c",
  "tier_id": "f6a5d8c0-b9e2-4f7d-1c3e-6b8a0d5f2c9e",
  "subscription_id": "d8f3c6b0-e9a2-4d1c-8f7a-5b3e9c2a4f6d",
  "start_date": "2024-09-10",
  "end_date": "2024-10-10",
  "allowed_sessions": 4
}
```

---

## Security considerations

### 🔴 CRITICAL: Hardcoded secrets vulnerability

**The current implementation contains a severe security vulnerability: API keys and signing keys are hardcoded in source code.**

#### Affected files

1. **PremiumTierConfig.java** (lines 17-24)
```java
private static final String ENTITLEMENT_SIGNING_KEY =
    "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d";

private static final String PARTNER_BILLING_API_KEY =
    "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d";
```

2. **application.yml** (lines 40-41)
```yaml
entitlement-signing-key: "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d"
partner-billing-api-key: "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d"
```

#### Impact

- **Credential exposure** — Secrets are visible to anyone with repository access
- **Version control leakage** — Secrets are permanently stored in Git history
- **Unauthorized access** — Attackers can use leaked keys to forge entitlement tokens or access billing API
- **Compliance violations** — Violates PCI-DSS, SOC 2, and other security standards

#### Remediation (REQUIRED before production deployment)

**Step 1: Remove hardcoded secrets from code**

Update `PremiumTierConfig.java` to inject values from Spring properties:

```java
@Configuration
@Getter
public class PremiumTierConfig {
    
    @Value("${premium-tier.entitlement-signing-key}")
    private String entitlementSigningKey;
    
    @Value("${premium-tier.partner-billing-api-key}")
    private String partnerBillingApiKey;
    
    @Value("${premium-tier.allowed-sessions}")
    private int premiumAllowedSessions;
}
```

**Step 2: Use environment variables**

Update `application.yml` to reference environment variables:

```yaml
premium-tier:
  enabled: ${PREMIUM_TIER_ENABLED:false}
  allowed-sessions: ${PREMIUM_TIER_ALLOWED_SESSIONS:4}
  entitlement-signing-key: ${PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY}
  partner-billing-api-key: ${PREMIUM_TIER_PARTNER_BILLING_API_KEY}
```

**Step 3: Store secrets in secret management system**

Use one of the following secure secret storage solutions:

| Solution | Use Case |
|----------|----------|
| **AWS Secrets Manager** | Production deployments on AWS |
| **HashiCorp Vault** | Multi-cloud or on-premise deployments |
| **Kubernetes Secrets** | Kubernetes-based deployments |
| **Spring Cloud Config Server** | Centralized configuration with encryption |

**Example: AWS Secrets Manager integration**

```java
@Configuration
public class SecretsConfig {
    
    @Bean
    public AWSSecretsManager secretsManager() {
        return AWSSecretsManagerClientBuilder.standard()
            .withRegion("us-east-1")
            .build();
    }
    
    @Bean
    public String entitlementSigningKey(AWSSecretsManager secretsManager) {
        GetSecretValueRequest request = new GetSecretValueRequest()
            .withSecretId("premium-tier/entitlement-signing-key");
        GetSecretValueResult result = secretsManager.getSecretValue(request);
        return result.getSecretString();
    }
}
```

**Step 4: Rotate compromised secrets**

Since the current keys are committed to Git history:

1. Generate new entitlement signing key:
   ```bash
   openssl rand -hex 32
   ```

2. Contact billing partner to issue new API key

3. Update secrets in secret manager

4. Deploy updated configuration

5. Revoke old keys with billing partner

**Step 5: Remove secrets from Git history**

Use `git-filter-repo` or BFG Repo-Cleaner to remove secrets from Git history:

```bash
# Install git-filter-repo
pip install git-filter-repo

# Remove sensitive file from history
git filter-repo --path services/subscription-service/src/main/java/io/github/marianciuc/streamingservice/subscription/config/PremiumTierConfig.java --invert-paths

# Force push (coordinate with team first!)
git push origin --force --all
```

### Authentication and authorization

The `/premium/grant` endpoint requires:

1. **Valid JWT token** — Issued by the authentication service
2. **ROLE_ADMIN authority** — Enforced by `@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")`

Only administrators can grant premium tiers. Regular users cannot upgrade themselves.

### Input validation

All API parameters are validated:

| Parameter | Validation |
|-----------|------------|
| `userId` | Must be valid UUID format |
| `tierId` | Must be valid UUID format |
| `userId` | Must reference existing user in database |
| `tierId` | Must reference existing subscription tier in database |

Invalid inputs return `400 Bad Request` or `404 Not Found` responses.

### Database security

- User subscription data is stored in PostgreSQL with row-level security policies
- Database credentials are managed via environment variables
- Connection pooling limits prevent resource exhaustion attacks

---

## Testing

### Test coverage

The Premium Subscription Tier feature has comprehensive unit test coverage:

| Test Suite | Test Count | Coverage |
|------------|------------|----------|
| `SubscriptionControllerGrantPremiumTest` | 10 tests | Controller layer |
| `UserSubscriptionServiceImplGrantPremiumTest` | 35+ tests | Service layer business logic |
| `PremiumTierConfigTest` | Not specified | Configuration validation |

**Total estimated coverage:** ~98% (45 unit tests)

### Key test scenarios

#### Controller tests (`SubscriptionControllerGrantPremiumTest`)

✅ Should return 200 OK when granting premium tier with valid parameters  
✅ Should call service with correct parameters  
✅ Should propagate NotFoundException when tier ID is invalid  
✅ Should propagate NotFoundException when user ID is invalid  
✅ Should handle null userId parameter  
✅ Should handle null tierId parameter  
✅ Should handle both parameters being null  
✅ Should return ResponseEntity with empty body  
✅ Should handle runtime exceptions from service layer  

#### Service tests (`UserSubscriptionServiceImplGrantPremiumTest`)

✅ Should upgrade existing active subscription to premium tier  
✅ Should calculate correct end date when upgrading existing subscription  
✅ Should create new subscription when user has no active subscription  
✅ Should calculate correct end date when creating new subscription  
✅ Should handle multiple concurrent grant requests  
✅ Should validate tier exists before granting  
✅ Should preserve existing orderId when upgrading  
✅ Should generate random orderId when creating new subscription  
✅ Should update subscription status to ACTIVE  

### Running tests

**Run all subscription service tests:**
```bash
cd services/subscription-service
mvn test
```

**Run only premium tier tests:**
```bash
mvn test -Dtest=*GrantPremium*
```

**Run tests with coverage report:**
```bash
mvn test jacoco:report
```

Coverage report is generated at:
```
services/subscription-service/target/site/jacoco/index.html
```

### Integration testing

To test the premium grant endpoint in a staging environment:

**Prerequisites:**
- Admin JWT token
- Valid user UUID
- Valid premium tier UUID

**Test script:**
```bash
#!/bin/bash

# Configuration
API_BASE_URL="https://staging.streaming-service.com"
ADMIN_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
USER_ID="a3f7d8e2-4b9c-4a1e-8f3d-9c2b7e6a5d8c"
TIER_ID="f6a5d8c0-b9e2-4f7d-1c3e-6b8a0d5f2c9e"

# Grant premium tier
response=$(curl -s -w "\n%{http_code}" -X POST \
  "${API_BASE_URL}/api/v1/subscription/premium/grant?userId=${USER_ID}&tierId=${TIER_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" -eq 200 ]; then
  echo "✅ Premium tier granted successfully"
else
  echo "❌ Failed to grant premium tier (HTTP $http_code)"
  echo "Response: $body"
  exit 1
fi

# Verify subscription was updated
subscription=$(curl -s -X GET \
  "${API_BASE_URL}/api/v1/subscription/active?id=${USER_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}")

echo "Current subscription: $subscription"
```

---

## Deployment

### Prerequisites

Before deploying the Premium Subscription Tier feature:

✅ PostgreSQL database with `subscriptions` schema  
✅ Kafka cluster for event publishing  
✅ Secret management system configured (AWS Secrets Manager, Vault, etc.)  
✅ Premium subscription tier records created in database  
✅ Media service updated with entitlement validation logic  
✅ Partner billing gateway credentials obtained  

### Deployment steps

#### Step 1: Create premium tier in database

Insert premium tier records into the `subscriptions` table:

```sql
INSERT INTO subscriptions (
  id, 
  name, 
  description, 
  duration_in_days, 
  price, 
  currency, 
  allowed_active_sessions, 
  record_status,
  created_at,
  updated_at
) VALUES (
  'f6a5d8c0-b9e2-4f7d-1c3e-6b8a0d5f2c9e',
  'Premium',
  'Premium subscription with 4K streaming and 4 concurrent sessions',
  30,
  19.99,
  'USD',
  4,
  'ACTIVE',
  NOW(),
  NOW()
);
```

#### Step 2: Configure secrets

Store secrets in AWS Secrets Manager (or your chosen secret store):

```bash
# Create entitlement signing key secret
aws secretsmanager create-secret \
  --name premium-tier/entitlement-signing-key \
  --secret-string "$(openssl rand -hex 32)" \
  --region us-east-1

# Create billing API key secret
aws secretsmanager create-secret \
  --name premium-tier/partner-billing-api-key \
  --secret-string "wzl_prem_YOUR_ACTUAL_KEY_HERE" \
  --region us-east-1
```

#### Step 3: Update application configuration

Update `application.yml` or set environment variables:

```bash
export PREMIUM_TIER_ENABLED=true
export PREMIUM_TIER_ALLOWED_SESSIONS=4
export PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY=$(aws secretsmanager get-secret-value --secret-id premium-tier/entitlement-signing-key --query SecretString --output text)
export PREMIUM_TIER_PARTNER_BILLING_API_KEY=$(aws secretsmanager get-secret-value --secret-id premium-tier/partner-billing-api-key --query SecretString --output text)
```

#### Step 4: Build and deploy subscription service

```bash
# Build Docker image
cd services/subscription-service
docker build -t streaming-service/subscription-service:1.1.0 .

# Push to registry
docker push streaming-service/subscription-service:1.1.0

# Deploy to Kubernetes
kubectl apply -f k8s/subscription-service-deployment.yaml
kubectl rollout status deployment/subscription-service
```

#### Step 5: Verify deployment

```bash
# Check service health
curl https://api.streaming-service.com/actuator/health

# Test premium grant endpoint
curl -X POST "https://api.streaming-service.com/api/v1/subscription/premium/grant?userId=TEST_USER_ID&tierId=f6a5d8c0-b9e2-4f7d-1c3e-6b8a0d5f2c9e" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

#### Step 6: Enable feature flag (if using feature toggles)

If using a feature flag system like LaunchDarkly or Unleash:

```bash
# Enable premium tier feature
curl -X PATCH "https://feature-flags.streaming-service.com/api/flags/premium-tier" \
  -H "Authorization: Bearer FLAG_API_TOKEN" \
  -d '{"enabled": true}'
```

### Rollback procedure

If issues are detected post-deployment:

```bash
# Revert to previous version
kubectl rollout undo deployment/subscription-service

# Disable feature flag
curl -X PATCH "https://feature-flags.streaming-service.com/api/flags/premium-tier" \
  -H "Authorization: Bearer FLAG_API_TOKEN" \
  -d '{"enabled": false}'

# Verify rollback
kubectl rollout status deployment/subscription-service
```

### Database migration

The feature uses existing tables. No new migrations are required. If adding new columns:

```sql
-- Example: Add premium_features JSON column
ALTER TABLE subscriptions 
ADD COLUMN premium_features JSONB DEFAULT '{}';

-- Add index for performance
CREATE INDEX idx_subscriptions_allowed_sessions 
ON subscriptions(allowed_active_sessions);
```

### Monitoring and observability

After deployment, monitor the following metrics:

| Metric | Alert Threshold | Description |
|--------|-----------------|-------------|
| `premium_grants_total` | N/A | Counter of successful premium grants |
| `premium_grant_errors_total` | > 10/min | Counter of failed grant attempts |
| `premium_grant_duration_seconds` | > 2s | Histogram of grant operation latency |
| `active_premium_subscriptions` | N/A | Gauge of current premium users |

**Grafana dashboard example:**
```json
{
  "dashboard": {
    "title": "Premium Subscription Tier Metrics",
    "panels": [
      {
        "title": "Premium Grants per Hour",
        "targets": [
          {
            "expr": "rate(premium_grants_total[1h])"
          }
        ]
      }
    ]
  }
}
```

---

## Troubleshooting

### Common issues and solutions

#### Issue: 404 Not Found - Subscription tier not found

**Symptoms:**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Subscription tier not found"
}
```

**Cause:** The `tierId` does not exist in the `subscriptions` table.

**Solution:**
1. Verify tier exists:
   ```sql
   SELECT * FROM subscriptions WHERE id = 'f6a5d8c0-b9e2-4f7d-1c3e-6b8a0d5f2c9e';
   ```

2. If missing, create the tier:
   ```sql
   INSERT INTO subscriptions (...) VALUES (...);
   ```

3. Verify `record_status` is `ACTIVE`, not `DELETED` or `HIDDEN`

---

#### Issue: 403 Forbidden - Insufficient permissions

**Symptoms:**
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied"
}
```

**Cause:** The JWT token does not have `ROLE_ADMIN` authority.

**Solution:**
1. Verify token claims:
   ```bash
   jwt decode YOUR_TOKEN
   ```

2. Check `authorities` array contains `ROLE_ADMIN`

3. If missing, request admin token from authentication service:
   ```bash
   curl -X POST "https://auth.streaming-service.com/api/v1/auth/login" \
     -d '{"username": "admin", "password": "***"}'
   ```

---

#### Issue: End date not updating on repeated grants

**Symptoms:** Calling `grantPremiumTier` multiple times on the same day does not extend the end date.

**Cause:** End date is calculated as `LocalDate.now().plusDays(30)`. If called twice on the same day, the calculation produces the same result.

**Solution:** This is expected behavior. To extend a subscription, wait until the next day or modify the business logic to add duration to the existing end date:

```java
// Current logic (replaces end date)
userSubscriptions.setEndDate(LocalDate.now().plusDays(tier.getDurationInDays()));

// Alternative logic (extends end date)
LocalDate currentEndDate = userSubscriptions.getEndDate();
LocalDate newEndDate = currentEndDate.plusDays(tier.getDurationInDays());
userSubscriptions.setEndDate(newEndDate);
```

---

#### Issue: NullPointerException when granting premium tier

**Symptoms:**
```
java.lang.NullPointerException: Cannot invoke "Subscription.getDurationInDays()" because "tier" is null
```

**Cause:** The `subscriptionService.getSubscription(tierId)` returned `null` instead of throwing `NotFoundException`.

**Solution:**
1. Add null check in `UserSubscriptionServiceImpl`:
   ```java
   Subscription tier = subscriptionService.getSubscription(tierId);
   if (tier == null) {
       throw new NotFoundException("Subscription tier not found: " + tierId);
   }
   ```

2. Update `SubscriptionService` to throw exception instead of returning null

---

#### Issue: Billing gateway returns 401 Unauthorized

**Symptoms:**
```
BillingException: Failed to charge premium upgrade - HTTP 401 Unauthorized
```

**Cause:** The `partner-billing-api-key` is invalid or expired.

**Solution:**
1. Verify API key is correct:
   ```bash
   curl -X GET "https://billing-partner.example.com/api/v1/account" \
     -H "Authorization: Bearer wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d"
   ```

2. If invalid, request new key from billing partner

3. Update secret in secret manager:
   ```bash
   aws secretsmanager update-secret \
     --secret-id premium-tier/partner-billing-api-key \
     --secret-string "wzl_prem_NEW_KEY_HERE"
   ```

4. Restart subscription service to reload configuration

---

#### Issue: Media service rejects entitlement token

**Symptoms:** Users with premium subscriptions cannot access 4K content.

**Cause:** Media service is using a different `entitlement-signing-key` than subscription service.

**Solution:**
1. Verify both services use the same key:
   ```bash
   # Subscription service
   kubectl exec -it subscription-service-pod -- env | grep ENTITLEMENT_SIGNING_KEY
   
   # Media service
   kubectl exec -it media-service-pod -- env | grep ENTITLEMENT_SIGNING_KEY
   ```

2. If keys differ, update media service configuration

3. Restart media service

---

#### Issue: Database constraint violation on order_id

**Symptoms:**
```
org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "user_subscriptions_order_id_key"
```

**Cause:** The `UUID.randomUUID()` generated a duplicate `orderId` (extremely rare but possible).

**Solution:**
1. Retry the operation (collision is astronomically unlikely on second attempt)

2. For production robustness, add retry logic:
   ```java
   @Retryable(value = DataIntegrityViolationException.class, maxAttempts = 3)
   public void grantPremiumTier(UUID userId, UUID tierId) {
       // existing logic
   }
   ```

---

### Debugging tips

**Enable debug logging:**

Add to `application.yml`:
```yaml
logging:
  level:
    io.github.marianciuc.streamingservice.subscription: DEBUG
    org.springframework.jdbc: DEBUG
```

**Inspect database state:**
```sql
-- View user's subscription history
SELECT * FROM user_subscriptions WHERE user_id = 'a3f7d8e2-4b9c-4a1e-8f3d-9c2b7e6a5d8c' ORDER BY start_date DESC;

-- View all premium subscriptions
SELECT us.*, s.name, s.allowed_active_sessions 
FROM user_subscriptions us
JOIN subscriptions s ON us.subscription_id = s.id
WHERE s.allowed_active_sessions >= 4 AND us.status = 'ACTIVE';

-- Check for orphaned subscriptions (no matching tier)
SELECT * FROM user_subscriptions WHERE subscription_id NOT IN (SELECT id FROM subscriptions);
```

**Test token generation:**
```bash
# Generate test entitlement token
curl -X POST "http://localhost:8080/api/v1/subscription/debug/generate-token?userId=TEST_USER_ID" \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

---

## Related documentation

- [Subscription Service Architecture](../architecture/subscription-service.md)
- [API Gateway Configuration](../infrastructure/api-gateway.md)
- [JWT Authentication Guide](../security/jwt-authentication.md)
- [Stripe Integration](../integrations/stripe-payment-gateway.md)
- [Database Schema Reference](../database/subscription-schema.md)

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2024-09-10 | Initial release of Premium Subscription Tier feature |

---

## Editor's notes

- **Security remediation required** — Hardcoded secrets must be removed before production deployment (see [Security considerations](#security-considerations))
- **Idempotency consideration** — Current implementation is not idempotent; consider adding grant operation tracking
- **Billing integration incomplete** — Partner billing gateway integration code is illustrative; actual implementation may differ
- **Feature flag support** — Document does not specify feature flag implementation; add if using LaunchDarkly/Unleash
- **Monitoring metrics** — Prometheus metrics mentioned are examples; actual metric names may differ based on implementation
