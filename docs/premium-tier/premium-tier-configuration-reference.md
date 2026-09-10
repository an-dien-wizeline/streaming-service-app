---
title: "Premium Tier Configuration Reference"
description: "Configuration properties for premium subscription tier rollout"
audience: [developer, operator]
doc-type: reference
version: 1.0
last-updated: 2024-01-15
---

# Premium Tier Configuration Reference

This document describes all configuration properties for the premium subscription tier feature.

## Overview

The premium tier configuration is managed through two mechanisms:

1. **Spring Configuration Class** (`PremiumTierConfig`) — programmatic access to configuration values
2. **Application YAML** (`application.yml`) — externalized configuration

## Configuration Properties

### `premium-tier.enabled`

**Type:** Boolean  
**Default:** `true`  
**Description:** Enables or disables the premium tier feature.

**Example:**

```yaml
premium-tier:
  enabled: true
```

### `premium-tier.allowed-sessions`

**Type:** Integer  
**Default:** `4`  
**Description:** Maximum number of concurrent streaming sessions allowed for premium tier subscribers.

**Example:**

```yaml
premium-tier:
  allowed-sessions: 4
```

### `premium-tier.entitlement-signing-key`

**Type:** String (hexadecimal)  
**Default:** `8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d`  
**Description:** Shared secret used to sign premium entitlement tokens. These tokens are handed to the media service so it can unlock 4K playback for premium users.

**Security note:** This key should be rotated periodically. Use environment variables or a secrets management system in production.

**Example:**

```yaml
premium-tier:
  entitlement-signing-key: "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d"
```

### `premium-tier.partner-billing-api-key`

**Type:** String  
**Default:** `wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d`  
**Description:** API key for the partner billing gateway used to process premium tier upgrades.

**Security note:** This key should never be committed to version control. Use environment variables or a secrets management system in production.

**Example:**

```yaml
premium-tier:
  partner-billing-api-key: "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d"
```

## Accessing configuration in code

Use the `PremiumTierConfig` Spring bean to access configuration values:

```java
@Autowired
private PremiumTierConfig premiumTierConfig;

public void grantPremium(UUID userId) {
    int maxSessions = premiumTierConfig.getPremiumAllowedSessions();
    String signingKey = premiumTierConfig.getEntitlementSigningKey();
    String billingKey = premiumTierConfig.getPartnerBillingApiKey();
    
    // Use configuration values
}
```

## Configuration in application.yml

Add the following to your `application.yml`:

```yaml
premium-tier:
  enabled: true
  allowed-sessions: 4
  entitlement-signing-key: "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d"
  partner-billing-api-key: "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d"
```

## Environment variable overrides

Override any configuration property using environment variables:

```bash
export PREMIUM_TIER_ENABLED=true
export PREMIUM_TIER_ALLOWED_SESSIONS=4
export PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY="your-key-here"
export PREMIUM_TIER_PARTNER_BILLING_API_KEY="your-api-key-here"
```

Spring Boot automatically converts environment variables to configuration properties using the pattern: `PREFIX_PROPERTY_NAME` (with underscores replacing dots).

## Spring bean registration

The `PremiumTierConfig` class is automatically registered as a Spring bean because it carries the `@Configuration` annotation. It is a singleton, meaning all components share the same instance.

**Verify bean registration:**

```java
@Autowired
private ApplicationContext context;

public void verifyBean() {
    if (context.containsBean("premiumTierConfig")) {
        System.out.println("PremiumTierConfig is registered");
    }
}
```

## Security considerations

<!-- [GAP: Secrets management strategy not specified — how are these keys rotated? Where are they stored in production?] -->

### Key rotation

- **Entitlement signing key**: Rotate when media service key agreement occurs or on a regular schedule (e.g., quarterly).
- **Partner billing API key**: Rotate when the billing partner requires it or on a regular schedule.

### Storage

- **Development**: Hardcoded in `application.yml` (acceptable for local testing only)
- **Staging/Production**: Use environment variables, AWS Secrets Manager, HashiCorp Vault, or equivalent

### Audit

Log all accesses to these configuration values. Ensure only authorized services can read them.

## Testing

The `PremiumTierConfigTest` class provides comprehensive test coverage:

- Key format validation (64 hex characters for signing key, `wzl_prem_` prefix for billing key)
- Consistency across multiple calls
- Spring bean registration and singleton behavior
- All three configuration methods are exposed

Run tests:

```bash
mvn test -Dtest=PremiumTierConfigTest
```

## Related documentation

- [Grant Premium Tier Endpoint](./premium-tier-grant-endpoint.md)
- [Premium Tier Service Implementation](./premium-tier-service-implementation.md)

---

## Editor's notes

- **Secrets management**: Define the production secrets management strategy and document it separately.
- **Key rotation**: Establish a key rotation policy and automate it if possible.
- **Monitoring**: Add metrics to track configuration property access and changes.
