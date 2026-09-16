---
title: "SECURITY ADVISORY: Premium Tier Grant Endpoint"
description: "Critical security vulnerabilities in the premium tier grant endpoint and remediation steps"
audience: [developer, admin, security-team]
doc-type: explanation
severity: CRITICAL
version: 1.0
last-updated: 2025-01-15
---

# 🚨 SECURITY ADVISORY: Premium Tier Grant Endpoint

**Severity:** CRITICAL  
**Status:** OPEN  
**Affected Component:** `subscription-service`  
**Affected Endpoint:** `POST /api/v1/subscription/premium/grant`  
**Branch:** `feature/premium_subscription`  
**Discovery Date:** 2025-01-15  

---

## Executive summary

The premium tier grant endpoint (`POST /api/v1/subscription/premium/grant`) has **no authentication or authorization controls**, allowing any client with network access to grant premium subscriptions to any user. Additionally, sensitive cryptographic keys and API credentials are **hardcoded in source code and configuration files**, exposing them to anyone with repository access.

**Impact:** Unauthorized privilege escalation, revenue loss, credential exposure, potential account takeover.

**Recommendation:** **DO NOT deploy this feature to production** until all vulnerabilities are remediated.

---

## Vulnerability 1: Missing authentication and authorization

### Description

The `/premium/grant` endpoint lacks the `@PreAuthorize` annotation, making it accessible to unauthenticated users.

### Affected code

**File:** `services/subscription-service/src/main/java/io/github/marianciuc/streamingservice/subscription/controller/SubscriptionController.java`

**Lines 105-110:**
```java
@PostMapping("/premium/grant")
public ResponseEntity<Void> grantPremiumTier(@RequestParam("userId") UUID userId,
                                             @RequestParam("tierId") UUID tierId) {
    userSubscriptionService.grantPremiumTier(userId, tierId);
    return ResponseEntity.ok().build();
}
```

**Missing annotation:**
```java
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
```

### Comparison with other endpoints

Other admin endpoints in the same controller are properly protected:

```java
@PostMapping
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")  // ✅ Protected
public ResponseEntity<SubscriptionResponse> addSubscription(@RequestBody @Valid SubscriptionRequest request) {
    return ResponseEntity.ok(subscriptionService.createSubscription(request));
}

@DeleteMapping
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")  // ✅ Protected
public ResponseEntity<Void> deleteSubscription(@RequestParam("id") UUID id) {
    subscriptionService.deleteSubscription(id);
    return ResponseEntity.ok().build();
}
```

### Impact

An attacker can:

1. **Grant premium subscriptions without payment:**
   ```bash
   curl -X POST "http://production-api.example.com/api/v1/subscription/premium/grant?userId=<target-user>&tierId=<premium-tier-id>"
   ```

2. **Bypass billing entirely:**
   - Premium tiers typically cost $19.99/month
   - Each unauthorized grant represents lost revenue

3. **Create financial liability:**
   - Premium users consume more bandwidth (4K streaming)
   - Increased infrastructure costs without corresponding revenue

4. **Enable account takeover:**
   - Attacker grants premium to their own account
   - Uses premium features (e.g., 4 concurrent sessions) to share account widely

### Proof of concept

**Step 1:** Identify a premium tier ID:
```sql
SELECT id FROM subscriptions WHERE name = 'Premium';
-- Result: f1e2d3c4-b5a6-7890-cdef-123456789abc
```

**Step 2:** Grant premium to an arbitrary user (no authentication required):
```bash
curl -X POST "http://localhost:8080/api/v1/subscription/premium/grant?userId=a1b2c3d4-e5f6-7890-abcd-ef1234567890&tierId=f1e2d3c4-b5a6-7890-cdef-123456789abc"
```

**Step 3:** Verify the grant:
```sql
SELECT * FROM user_subscriptions WHERE user_id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890' AND status = 'ACTIVE';
-- Premium subscription is now active
```

### CVSS score

**CVSS v3.1:** 9.1 (CRITICAL)

**Vector:** `CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:H/A:H`

- **Attack Vector (AV):** Network — exploitable remotely
- **Attack Complexity (AC):** Low — no special conditions required
- **Privileges Required (PR):** None — no authentication needed
- **User Interaction (UI):** None — fully automated exploit
- **Scope (S):** Unchanged — impact limited to subscription service
- **Confidentiality (C):** None — no data exfiltration
- **Integrity (I):** High — unauthorized modification of subscription data
- **Availability (A):** High — potential for resource exhaustion via mass grants

### Remediation

**Priority:** CRITICAL  
**Effort:** 5 minutes  
**Testing required:** Unit tests, integration tests

**Step 1:** Add the `@PreAuthorize` annotation:

```java
@PostMapping("/premium/grant")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")  // Add this line
public ResponseEntity<Void> grantPremiumTier(@RequestParam("userId") UUID userId,
                                             @RequestParam("tierId") UUID tierId) {
    userSubscriptionService.grantPremiumTier(userId, tierId);
    return ResponseEntity.ok().build();
}
```

**Step 2:** Verify the fix with a test:

```java
@Test
@WithMockUser(authorities = "ROLE_USER")  // Non-admin user
public void grantPremiumTier_whenNonAdmin_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/subscription/premium/grant")
            .param("userId", userId.toString())
            .param("tierId", tierId.toString()))
        .andExpect(status().isForbidden());
}

@Test
@WithMockUser(authorities = "ROLE_ADMIN")  // Admin user
public void grantPremiumTier_whenAdmin_returns200() throws Exception {
    mockMvc.perform(post("/api/v1/subscription/premium/grant")
            .param("userId", userId.toString())
            .param("tierId", tierId.toString()))
        .andExpect(status().isOk());
}
```

**Step 3:** Deploy the fix immediately.

---

## Vulnerability 2: Hardcoded secrets in source code

### Description

Sensitive cryptographic keys and API credentials are hardcoded in `PremiumTierConfig.java` and `application.yml`, making them visible to anyone with repository access.

### Affected files

#### File 1: `PremiumTierConfig.java`

**Location:** `services/subscription-service/src/main/java/io/github/marianciuc/streamingservice/subscription/config/PremiumTierConfig.java`

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

#### File 2: `application.yml`

**Location:** `services/subscription-service/src/main/resources/application.yml`

```yaml
premium-tier:
  enabled: true
  allowed-sessions: 4
  entitlement-signing-key: "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d"
  partner-billing-api-key: "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d"
```

### Exposed secrets

| Secret | Purpose | Exposure Risk |
|--------|---------|---------------|
| `ENTITLEMENT_SIGNING_KEY` | Signs JWT tokens for media service to enable 4K playback | An attacker can forge entitlement tokens, granting 4K access to any user |
| `PARTNER_BILLING_API_KEY` | Authenticates with partner billing gateway | An attacker can impersonate the subscription service, creating fraudulent billing records |

### Impact

#### Scenario 1: Forged entitlement tokens

An attacker with the `ENTITLEMENT_SIGNING_KEY` can:

1. **Generate valid entitlement tokens:**
   ```java
   String token = Jwts.builder()
       .setSubject(userId.toString())
       .claim("tier", "premium")
       .signWith(SignatureAlgorithm.HS256, "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d")
       .compact();
   ```

2. **Enable 4K streaming for free accounts:**
   - Pass the forged token to the media service
   - Media service validates the signature (it matches!)
   - 4K playback is unlocked without a premium subscription

#### Scenario 2: Billing fraud

An attacker with the `PARTNER_BILLING_API_KEY` can:

1. **Create fraudulent billing records:**
   ```bash
   curl -X POST "https://billing.partner.example.com/v1/premium-grants" \
     -H "Authorization: Bearer wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d" \
     -d '{"userId":"attacker","amount":-1000.00}'
   ```

2. **Manipulate billing data:**
   - Issue refunds to attacker's account
   - Delete legitimate billing records
   - Exfiltrate customer payment information (if the gateway exposes such data)

### Exposure timeline

These secrets have been committed to Git and are visible in:

- **Current branch:** `feature/premium_subscription`
- **Commit history:** All commits since the feature was added
- **GitHub/GitLab:** If the repository is hosted on a platform, secrets are visible in the web UI
- **CI/CD logs:** If the configuration file is printed during builds, secrets appear in logs
- **Developer machines:** Anyone who has cloned the repository has a copy of the secrets

**Assumption:** Treat these secrets as **publicly known**.

### CVSS score

**CVSS v3.1:** 8.2 (HIGH)

**Vector:** `CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N`

- **Attack Vector (AV):** Network — secrets enable remote attacks
- **Attack Complexity (AC):** Low — secrets are plaintext, no decryption needed
- **Privileges Required (PR):** Low — requires repository access (developer, contractor, ex-employee)
- **User Interaction (UI):** None
- **Scope (S):** Unchanged
- **Confidentiality (C):** High — secrets enable data exfiltration from billing gateway
- **Integrity (I):** High — secrets enable forging entitlement tokens
- **Availability (A):** None

### Remediation

**Priority:** CRITICAL  
**Effort:** 1-2 hours  
**Testing required:** Integration tests with secret management system

#### Step 1: Rotate the exposed secrets immediately

**Action:** Generate new secrets and update them in your secret management system (AWS Secrets Manager, HashiCorp Vault, etc.).

**Entitlement signing key:**
```bash
# Generate a new 256-bit key
openssl rand -hex 32
# Output: <new-key>
```

**Partner billing API key:**
- Contact the partner billing gateway provider
- Request a new API key
- Revoke the old key (`wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d`)

#### Step 2: Remove hardcoded secrets from source code

**Before:**
```java
@Configuration
@Getter
public class PremiumTierConfig {
    private static final String ENTITLEMENT_SIGNING_KEY =
            "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d";

    private static final String PARTNER_BILLING_API_KEY =
            "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d";

    private static final int PREMIUM_ALLOWED_SESSIONS = 4;
    // ...
}
```

**After:**
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

#### Step 3: Update `application.yml` to reference environment variables

**Before:**
```yaml
premium-tier:
  enabled: true
  allowed-sessions: 4
  entitlement-signing-key: "8f3d9a2c7b1e4f6a5d8c0b9e2a4f7d1c3e6b8a0d5f2c9e7b4a1d8f3c6b0e9a2d"
  partner-billing-api-key: "wzl_prem_9c8b7a6d5e4f3a2b1c0d9e8f7a6b5c4d"
```

**After:**
```yaml
premium-tier:
  enabled: ${PREMIUM_TIER_ENABLED:true}
  allowed-sessions: ${PREMIUM_TIER_ALLOWED_SESSIONS:4}
  entitlement-signing-key: ${PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY}
  partner-billing-api-key: ${PREMIUM_TIER_PARTNER_BILLING_API_KEY}
```

#### Step 4: Load secrets from environment variables in production

**Example (Docker Compose):**
```yaml
services:
  subscription-service:
    image: subscription-service:latest
    environment:
      PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY: ${ENTITLEMENT_KEY_FROM_VAULT}
      PREMIUM_TIER_PARTNER_BILLING_API_KEY: ${BILLING_KEY_FROM_VAULT}
```

**Example (Kubernetes):**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: premium-tier-secrets
type: Opaque
data:
  entitlement-key: <base64-encoded-key>
  billing-api-key: <base64-encoded-key>
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: subscription-service
spec:
  template:
    spec:
      containers:
      - name: subscription-service
        env:
        - name: PREMIUM_TIER_ENTITLEMENT_SIGNING_KEY
          valueFrom:
            secretKeyRef:
              name: premium-tier-secrets
              key: entitlement-key
        - name: PREMIUM_TIER_PARTNER_BILLING_API_KEY
          valueFrom:
            secretKeyRef:
              name: premium-tier-secrets
              key: billing-api-key
```

#### Step 5: Scrub secrets from Git history

**Warning:** This rewrites Git history and requires coordination with all developers.

**Option 1: Use BFG Repo-Cleaner (recommended):**
```bash
# Install BFG
brew install bfg

# Clone a fresh copy
git clone --mirror git@github.com:yourorg/streaming-service-app.git

# Remove secrets
bfg --replace-text secrets.txt streaming-service-app.git

# Force push (requires coordination)
cd streaming-service-app.git
git reflog expire --expire=now --all && git gc --prune=now --aggressive
git push --force
```

**Option 2: Use git-filter-repo:**
```bash
pip install git-filter-repo

git filter-repo --path services/subscription-service/src/main/resources/application.yml --invert-paths
git filter-repo --path services/subscription-service/src/main/java/io/github/marianciuc/streamingservice/subscription/config/PremiumTierConfig.java --invert-paths
```

**Option 3: Treat the repository as compromised:**
- Create a new repository
- Copy only the latest code (without secrets)
- Migrate all developers to the new repository
- Archive the old repository

#### Step 6: Add pre-commit hooks to prevent future leaks

**Install `detect-secrets`:**
```bash
pip install detect-secrets
```

**Create `.pre-commit-config.yaml`:**
```yaml
repos:
  - repo: https://github.com/Yelp/detect-secrets
    rev: v1.4.0
    hooks:
      - id: detect-secrets
        args: ['--baseline', '.secrets.baseline']
```

**Initialize baseline:**
```bash
detect-secrets scan > .secrets.baseline
```

**Install the hook:**
```bash
pre-commit install
```

---

## Vulnerability 3: No audit logging

### Description

The endpoint does not log who granted premium access, when, or to whom. This prevents:

- **Incident response:** Unable to identify unauthorized grants
- **Compliance:** Fails SOX, PCI-DSS, and GDPR audit requirements
- **Forensics:** No trail for investigating abuse

### Impact

- **Regulatory fines:** GDPR Article 30 requires audit logs for data processing activities
- **Delayed incident response:** Cannot determine the scope of a breach
- **Accountability gaps:** No way to attribute actions to specific admins

### Remediation

**Priority:** HIGH  
**Effort:** 30 minutes

Add structured logging:

```java
@PostMapping("/premium/grant")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
public ResponseEntity<Void> grantPremiumTier(@RequestParam("userId") UUID userId,
                                             @RequestParam("tierId") UUID tierId,
                                             Authentication authentication) {
    String adminId = ((JwtUserDetails) authentication.getPrincipal()).getId().toString();
    
    log.info("Premium tier grant initiated: userId={}, tierId={}, grantedBy={}, timestamp={}",
        userId, tierId, adminId, Instant.now());
    
    try {
        userSubscriptionService.grantPremiumTier(userId, tierId);
        
        log.info("Premium tier granted successfully: userId={}, tierId={}, grantedBy={}",
            userId, tierId, adminId);
        
        return ResponseEntity.ok().build();
        
    } catch (Exception e) {
        log.error("Premium tier grant failed: userId={}, tierId={}, grantedBy={}, error={}",
            userId, tierId, adminId, e.getMessage());
        throw e;
    }
}
```

**Additional recommendation:** Store audit logs in a separate, append-only database table:

```sql
CREATE TABLE premium_grant_audit (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    tier_id UUID NOT NULL,
    granted_by UUID NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL
);
```

---

## Vulnerability 4: No rate limiting

### Description

The endpoint has no rate limiting, allowing an attacker to:

- Grant premium to thousands of accounts in seconds
- Cause database performance degradation
- Exhaust infrastructure resources (bandwidth, storage)

### Impact

- **Financial loss:** Mass unauthorized premium grants
- **Service degradation:** Database overload affects all users
- **Abuse amplification:** Attackers can automate grants via scripts

### Remediation

**Priority:** MEDIUM  
**Effort:** 2 hours

Implement rate limiting using Spring's `@RateLimiter` (if using Resilience4j) or a custom solution:

```java
@PostMapping("/premium/grant")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
@RateLimiter(name = "premiumGrant", fallbackMethod = "rateLimitFallback")
public ResponseEntity<Void> grantPremiumTier(@RequestParam("userId") UUID userId,
                                             @RequestParam("tierId") UUID tierId) {
    userSubscriptionService.grantPremiumTier(userId, tierId);
    return ResponseEntity.ok().build();
}

public ResponseEntity<Void> rateLimitFallback(UUID userId, UUID tierId, Exception e) {
    return ResponseEntity.status(429).build();
}
```

**Configuration (`application.yml`):**
```yaml
resilience4j:
  ratelimiter:
    instances:
      premiumGrant:
        limitForPeriod: 100
        limitRefreshPeriod: 1h
        timeoutDuration: 0
```

---

## Summary of vulnerabilities

| ID | Vulnerability | Severity | CVSS | Status |
|----|---------------|----------|------|--------|
| 1 | Missing authentication/authorization | CRITICAL | 9.1 | OPEN |
| 2 | Hardcoded secrets in source code | CRITICAL | 8.2 | OPEN |
| 3 | No audit logging | HIGH | N/A | OPEN |
| 4 | No rate limiting | MEDIUM | N/A | OPEN |

---

## Deployment checklist

**DO NOT deploy to production until all items are checked:**

- [ ] **CRITICAL:** Add `@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")` to `/premium/grant` endpoint
- [ ] **CRITICAL:** Rotate `ENTITLEMENT_SIGNING_KEY` and `PARTNER_BILLING_API_KEY`
- [ ] **CRITICAL:** Remove hardcoded secrets from `PremiumTierConfig.java` and `application.yml`
- [ ] **CRITICAL:** Load secrets from environment variables or secret management system
- [ ] **HIGH:** Add audit logging for all premium grants
- [ ] **HIGH:** Scrub secrets from Git history or treat repository as compromised
- [ ] **MEDIUM:** Implement rate limiting (100 grants per hour per admin)
- [ ] **MEDIUM:** Add integration tests for authentication/authorization
- [ ] **MEDIUM:** Configure network-level access controls (firewall, API gateway)
- [ ] **LOW:** Add monitoring alerts for spike in premium grants

---

## Incident response plan

If this feature has already been deployed to production:

### Immediate actions (within 1 hour)

1. **Disable the endpoint:**
   ```java
   @PostMapping("/premium/grant")
   public ResponseEntity<Void> grantPremiumTier(...) {
       throw new ServiceUnavailableException("This endpoint is temporarily disabled");
   }
   ```

2. **Block network access:**
   - Add firewall rules to block external access to the endpoint
   - Allow only internal admin IPs

3. **Audit database for unauthorized grants:**
   ```sql
   SELECT * FROM user_subscriptions
   WHERE subscription_id IN (SELECT id FROM subscriptions WHERE name = 'Premium')
     AND start_date >= '<deployment-date>'
   ORDER BY start_date DESC;
   ```

4. **Notify security team and legal counsel**

### Short-term actions (within 24 hours)

1. **Rotate all secrets** (see Vulnerability 2 remediation)
2. **Review application logs** for suspicious activity
3. **Identify affected users** and assess financial impact
4. **Revoke unauthorized premium subscriptions:**
   ```sql
   UPDATE user_subscriptions
   SET status = 'CANCELLED'
   WHERE id IN (<list-of-unauthorized-grants>);
   ```

### Long-term actions (within 1 week)

1. **Implement all remediations** listed in this advisory
2. **Conduct security training** for development team
3. **Perform penetration testing** on the subscription service
4. **Establish secure coding guidelines** and code review checklist
5. **Implement secret scanning** in CI/CD pipeline

---

## References

- [OWASP Top 10 - Broken Access Control (A01:2021)](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)
- [OWASP Top 10 - Cryptographic Failures (A02:2021)](https://owasp.org/Top10/A02_2021-Cryptographic_Failures/)
- [CWE-798: Use of Hard-coded Credentials](https://cwe.mitre.org/data/definitions/798.html)
- [CWE-862: Missing Authorization](https://cwe.mitre.org/data/definitions/862.html)

---

## Contact

**Security Team:** security@example.com  
**On-call Engineer:** +1-555-0100  
**Slack Channel:** #security-incidents

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-01-15 | Initial security advisory |

---

**This document is confidential and intended for internal use only. Do not share externally.**
