---
title: "Premium Tier Architecture Diagrams"
description: "Visual diagrams showing the premium tier feature architecture, data flow, and integration points"
audience: developer
doc-type: explanation
version: 1.0
last-updated: 2025-01-15
---

# Premium tier architecture diagrams

This document contains visual diagrams to help understand the premium tier feature's architecture and data flows.

## System architecture

```mermaid
graph TB
    subgraph "Client Layer"
        Admin[Admin User]
        Client[Client Application]
    end
    
    subgraph "API Gateway"
        Gateway[API Gateway<br/>⚠️ NO AUTH CHECK]
    end
    
    subgraph "Subscription Service"
        Controller[SubscriptionController<br/>/premium/grant]
        Service[UserSubscriptionService<br/>grantPremiumTier]
        Config[PremiumTierConfig<br/>⚠️ HARDCODED SECRETS]
    end
    
    subgraph "Data Layer"
        DB[(PostgreSQL<br/>subscriptions<br/>user_subscriptions)]
    end
    
    subgraph "External Services"
        MediaService[Media Service<br/>4K Playback]
        BillingGateway[Partner Billing Gateway]
    end
    
    Admin -->|POST /premium/grant| Gateway
    Client -->|POST /premium/grant| Gateway
    Gateway -->|⚠️ No Auth| Controller
    Controller -->|Call| Service
    Service -->|Read tier| DB
    Service -->|Update/Create subscription| DB
    Config -.->|Signs tokens| MediaService
    Config -.->|Authenticates| BillingGateway
    
    style Gateway fill:#ff6b6b
    style Config fill:#ff6b6b
    style Controller fill:#ffd93d
```

**Legend:**
- 🔴 Red = Critical security issue
- 🟡 Yellow = Warning

---

## Grant premium tier flow

```mermaid
sequenceDiagram
    actor Admin
    participant API as Subscription API<br/>/premium/grant
    participant Service as UserSubscriptionService
    participant DB as PostgreSQL
    
    Admin->>API: POST /premium/grant<br/>userId=abc, tierId=xyz
    Note over API: ⚠️ NO AUTHENTICATION CHECK
    
    API->>Service: grantPremiumTier(userId, tierId)
    
    Service->>DB: SELECT * FROM subscriptions<br/>WHERE id = tierId
    alt Tier not found
        DB-->>Service: Not found
        Service-->>API: NotFoundException
        API-->>Admin: 404 Not Found
    else Tier found
        DB-->>Service: Subscription tier
        
        Service->>DB: SELECT * FROM user_subscriptions<br/>WHERE user_id = userId<br/>AND status = 'ACTIVE'
        
        alt User has active subscription
            DB-->>Service: UserSubscriptions record
            Note over Service: Upgrade existing subscription
            Service->>Service: Set subscription = tier<br/>Set end_date = now() + tier.duration
            Service->>DB: UPDATE user_subscriptions<br/>SET subscription_id = tier.id,<br/>end_date = calculated_date
            DB-->>Service: Updated
        else No active subscription
            DB-->>Service: Empty result
            Note over Service: Create new subscription
            Service->>Service: Create UserSubscriptions<br/>orderId = random UUID
            Service->>DB: INSERT INTO user_subscriptions
            DB-->>Service: Created
        end
        
        Service-->>API: Success (void)
        API-->>Admin: 200 OK (empty body)
    end
```

---

## Data model

```mermaid
erDiagram
    SUBSCRIPTIONS ||--o{ USER_SUBSCRIPTIONS : "tier definition"
    USERS ||--o{ USER_SUBSCRIPTIONS : "has"
    ORDERS ||--o{ USER_SUBSCRIPTIONS : "payment for"
    
    SUBSCRIPTIONS {
        uuid id PK
        varchar name
        text description
        int duration_in_days
        decimal price
        varchar currency
        int allowed_active_sessions
        varchar record_status
        boolean is_temporary
        uuid next_subscription_id FK
        timestamp created_at
        timestamp updated_at
    }
    
    USER_SUBSCRIPTIONS {
        uuid id PK
        uuid user_id FK
        uuid order_id FK
        uuid subscription_id FK
        date start_date
        date end_date
        varchar status
    }
    
    USERS {
        uuid id PK
        varchar email
        varchar username
    }
    
    ORDERS {
        uuid id PK
        uuid user_id FK
        decimal amount
        varchar status
    }
```

---

## Subscription status lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING: Order created
    PENDING --> ACTIVE: Payment successful
    PENDING --> INACTIVE: Payment failed
    
    ACTIVE --> EXPIRED: End date reached
    ACTIVE --> CANCELLED: User cancels
    
    EXPIRED --> [*]
    CANCELLED --> [*]
    INACTIVE --> [*]
    
    note right of ACTIVE
        grantPremiumTier() updates
        existing ACTIVE subscription
        or creates new ACTIVE subscription
    end note
```

---

## Integration with media service

```mermaid
sequenceDiagram
    actor User
    participant Media as Media Service
    participant Sub as Subscription Service
    participant Config as PremiumTierConfig
    
    User->>Media: Request 4K stream
    Media->>Sub: GET /subscription/active<br/>userId=abc
    Sub->>Sub: Check user subscription
    
    alt User has premium subscription
        Sub->>Config: Get ENTITLEMENT_SIGNING_KEY<br/>⚠️ HARDCODED SECRET
        Config-->>Sub: Signing key
        Sub->>Sub: Generate JWT token<br/>claim: tier=premium
        Sub-->>Media: Entitlement token
        Media->>Media: Verify token signature<br/>using shared key
        Media-->>User: 4K stream enabled ✅
    else User does not have premium
        Sub-->>Media: No premium subscription
        Media-->>User: 4K stream denied ❌<br/>HD stream only
    end
```

---

## Current security vulnerabilities

```mermaid
graph LR
    subgraph "Attack Vectors"
        A1[Unauthenticated Client]
        A2[Malicious Insider]
        A3[Compromised Repository Access]
    end
    
    subgraph "Vulnerabilities"
        V1[No Authentication<br/>on /premium/grant<br/>CVSS 9.1]
        V2[Hardcoded Secrets<br/>in Source Code<br/>CVSS 8.2]
        V3[No Audit Logging<br/>HIGH]
        V4[No Rate Limiting<br/>MEDIUM]
    end
    
    subgraph "Impacts"
        I1[Unauthorized Premium Grants]
        I2[Revenue Loss]
        I3[Forged Entitlement Tokens]
        I4[Billing Fraud]
        I5[No Accountability]
        I6[Mass Exploitation]
    end
    
    A1 -->|Exploits| V1
    A2 -->|Exploits| V1
    A2 -->|Exploits| V3
    A1 -->|Exploits| V4
    A3 -->|Exploits| V2
    
    V1 --> I1
    V1 --> I2
    V2 --> I3
    V2 --> I4
    V3 --> I5
    V4 --> I6
    
    style V1 fill:#ff6b6b
    style V2 fill:#ff6b6b
    style V3 fill:#ffa500
    style V4 fill:#ffd93d
```

---

## Recommended secure architecture

```mermaid
graph TB
    subgraph "Client Layer"
        Admin[Admin User<br/>with JWT Token]
    end
    
    subgraph "API Gateway"
        Gateway[API Gateway<br/>✅ JWT Validation]
        RateLimit[Rate Limiter<br/>✅ 100 req/hour]
    end
    
    subgraph "Subscription Service"
        Controller[SubscriptionController<br/>✅ @PreAuthorize ROLE_ADMIN]
        Service[UserSubscriptionService<br/>grantPremiumTier]
        Config[PremiumTierConfig<br/>✅ Env Variables]
        Audit[Audit Logger<br/>✅ Who, When, What]
    end
    
    subgraph "Secret Management"
        Vault[AWS Secrets Manager<br/>or HashiCorp Vault]
    end
    
    subgraph "Data Layer"
        DB[(PostgreSQL)]
        AuditDB[(Audit Log DB<br/>Append-Only)]
    end
    
    Admin -->|POST /premium/grant<br/>+ JWT Token| Gateway
    Gateway -->|Validate JWT| Gateway
    Gateway -->|Check Rate Limit| RateLimit
    RateLimit -->|Allowed| Controller
    Controller -->|Check @PreAuthorize| Controller
    Controller -->|Call| Service
    Controller -->|Log action| Audit
    Service -->|Update| DB
    Audit -->|Write| AuditDB
    Config -->|Fetch secrets| Vault
    
    style Gateway fill:#95e1d3
    style Controller fill:#95e1d3
    style Config fill:#95e1d3
    style Audit fill:#95e1d3
    style RateLimit fill:#95e1d3
```

**Legend:**
- 🟢 Green = Secured component

---

## Deployment flow (with security fixes)

```mermaid
flowchart TD
    Start([Start Deployment]) --> Check1{Security fixes<br/>applied?}
    
    Check1 -->|No| Stop1([❌ STOP<br/>Do not deploy])
    Check1 -->|Yes| Check2{Secrets rotated?}
    
    Check2 -->|No| Rotate[Rotate all secrets<br/>in vault]
    Rotate --> Check3
    Check2 -->|Yes| Check3{Audit logging<br/>enabled?}
    
    Check3 -->|No| AddAudit[Add audit logging]
    AddAudit --> Check4
    Check3 -->|Yes| Check4{Rate limiting<br/>configured?}
    
    Check4 -->|No| AddRate[Configure rate limiter]
    AddRate --> Test
    Check4 -->|Yes| Test[Run integration tests]
    
    Test --> TestResult{Tests pass?}
    TestResult -->|No| Fix[Fix issues]
    Fix --> Test
    TestResult -->|Yes| Deploy[Deploy to production]
    
    Deploy --> Monitor[Monitor metrics<br/>and alerts]
    Monitor --> End([✅ Deployment Complete])
    
    style Stop1 fill:#ff6b6b
    style End fill:#95e1d3
```

---

## Data flow: Upgrade existing subscription

```mermaid
flowchart LR
    subgraph "Before Grant"
        Before[User Subscription<br/>tier: Basic<br/>end_date: 2025-01-31<br/>status: ACTIVE]
    end
    
    subgraph "Grant Premium"
        Action[POST /premium/grant<br/>tierId=premium-90-days]
    end
    
    subgraph "After Grant"
        After[User Subscription<br/>tier: Premium<br/>end_date: 2025-04-15<br/>status: ACTIVE]
    end
    
    Before --> Action
    Action --> After
    
    Note1[⚠️ Remaining time<br/>on Basic tier is LOST]
    Action -.-> Note1
    
    style Note1 fill:#ffd93d
```

---

## Data flow: Create new subscription

```mermaid
flowchart LR
    subgraph "Before Grant"
        Before[No active subscription<br/>for user]
    end
    
    subgraph "Grant Premium"
        Action[POST /premium/grant<br/>tierId=premium-90-days]
    end
    
    subgraph "After Grant"
        After[New User Subscription<br/>id: generated UUID<br/>orderId: generated UUID<br/>tier: Premium<br/>start_date: today<br/>end_date: today + 90 days<br/>status: ACTIVE]
    end
    
    Before --> Action
    Action --> After
    
    Note1[⚠️ orderId is random UUID<br/>not tied to payment]
    Action -.-> Note1
    
    style Note1 fill:#ffd93d
```

---

## See also

- [Premium Tier Feature Overview](../PREMIUM_TIER.md)
- [Premium Tier API Reference](./PREMIUM_TIER_API.md)
- [Security Advisory](./PREMIUM_TIER_SECURITY_ADVISORY.md)

---

**Last updated:** 2025-01-15  
**Version:** 1.0
