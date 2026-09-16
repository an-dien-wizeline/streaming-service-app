# Documentation Index

This directory contains comprehensive documentation for the streaming service application.

## Premium Tier Feature Documentation

The premium tier feature enables administrators to grant premium subscriptions to users. **⚠️ WARNING:** This feature has critical security vulnerabilities that must be fixed before production deployment.

### Documentation Files

| Document | Description | Audience |
|----------|-------------|----------|
| [Premium Tier Feature Overview](./PREMIUM_TIER.md) | Complete feature documentation including business context, configuration, API reference, integration guide, and deployment checklist | Developers, Admins |
| [Premium Tier API Reference](./api/PREMIUM_TIER_API.md) | Detailed API reference for the `/premium/grant` endpoint with examples in multiple languages | Developers |
| [Premium Tier Security Advisory](./api/PREMIUM_TIER_SECURITY_ADVISORY.md) | **CRITICAL** security vulnerabilities and remediation steps | Security Team, Developers, Admins |
| [Premium Tier Quick Start Guide](./PREMIUM_TIER_QUICKSTART.md) | Quick reference for granting premium subscriptions | Developers, Admins |

### Quick Links

- **Start here:** [Quick Start Guide](./PREMIUM_TIER_QUICKSTART.md)
- **Security issues:** [Security Advisory](./api/PREMIUM_TIER_SECURITY_ADVISORY.md) ⚠️ READ THIS FIRST
- **Full documentation:** [Feature Overview](./PREMIUM_TIER.md)
- **API details:** [API Reference](./api/PREMIUM_TIER_API.md)

### Critical Security Warnings

🚨 **DO NOT deploy the premium tier feature to production until these issues are fixed:**

1. **Missing authentication/authorization** (CVSS 9.1 - CRITICAL)
   - The `/premium/grant` endpoint has no access controls
   - Any client can grant premium subscriptions to any user
   - **Fix:** Add `@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")` annotation

2. **Hardcoded secrets** (CVSS 8.2 - CRITICAL)
   - Cryptographic keys and API credentials are committed to Git
   - Secrets are visible to anyone with repository access
   - **Fix:** Rotate secrets and move to environment variables or secret management system

See the [Security Advisory](./api/PREMIUM_TIER_SECURITY_ADVISORY.md) for complete details and remediation steps.

---

## Project Documentation

### Architecture

- [System Architecture](../diagrams/) - Architecture diagrams and system design
- [Database Schema](./PREMIUM_TIER.md#data-model) - Database schema for subscription management

### API Documentation

- [Subscription Service API](./api/) - REST API reference for subscription management
- [Premium Tier API](./api/PREMIUM_TIER_API.md) - Premium tier grant endpoint

### Configuration

- [Premium Tier Configuration](./PREMIUM_TIER.md#configuration) - Configuration properties and secrets management
- [Security Configuration](./PREMIUM_TIER.md#-critical-security-warning) - Authentication and authorization setup

### Deployment

- [Deployment Guide](./PREMIUM_TIER.md#deployment-guide) - Pre-deployment checklist and rollback plan
- [Monitoring and Observability](./PREMIUM_TIER.md#monitoring-and-observability) - Metrics, logging, and alerting

---

## Contributing to Documentation

### Documentation Standards

This documentation follows the [Diátaxis framework](https://diataxis.fr/) and adheres to these principles:

- **Active voice, second person, present tense** — "You can configure..." not "The user can configure..."
- **Complete, runnable code examples** — No `...` or `// rest of code`
- **Visible gaps over hidden errors** — Mark unknowns as `[GAP: description]`
- **Progressive disclosure** — Common path first, edge cases in variations

### Updating Documentation

When making code changes that affect the premium tier feature:

1. Update the relevant documentation file(s)
2. Update the `last-updated` date in the YAML frontmatter
3. Add an entry to the Changelog section
4. Test all code examples to ensure they still work

### Documentation Structure

```
docs/
├── README.md                              # This file
├── PREMIUM_TIER.md                        # Main feature documentation
├── PREMIUM_TIER_QUICKSTART.md            # Quick start guide
└── api/
    ├── PREMIUM_TIER_API.md               # API reference
    └── PREMIUM_TIER_SECURITY_ADVISORY.md # Security advisory
```

---

## Getting Help

- **Documentation issues:** Create a ticket in Jira (project: DOCS)
- **Technical questions:** Contact the subscription-service team via Slack (#subscription-service)
- **Security concerns:** Email security@example.com immediately

---

**Last updated:** 2025-01-15
