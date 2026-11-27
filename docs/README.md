# ZayloPe Documentation Index

This is a development baseline. Historical requirements and implementation summaries coexist; see [known issues](12-project-status/known-issues.md) for actual test failures, security advisories and documentation reconciliation still required. Source contracts and migrations take precedence where summaries conflict.

## Documentation Structure

| Folder | Purpose |
| --- | --- |
| [01-project/](01-project/project-overview.md) | Business overview, ZayloPe/Zaylo model, scope, and glossary |
| [02-requirements/](02-requirements/business-requirements.md) | Business, functional, non-functional requirements, and business rules |
| [03-architecture/](03-architecture/system-architecture.md) | System, frontend, backend architecture, and repository structure |
| [04-modules/](04-modules/public-website.md) | Per-module implementation, workflows, APIs, and test evidence |
| [05-database/](05-database/database-design.md) | Schema ER diagram, data dictionary, and Flyway migrations |
| [06-api/](06-api/api-reference.md) | REST API contracts and application error codes |
| [07-design/](07-design/design-system.md) | Design tokens, typography, components, and UX states |
| [08-security/](08-security/security-architecture.md) | Authentication, session cookies, validation, and data protection |
| [09-development/](09-development/local-setup.md) | Local setup, environment variables, coding standards, troubleshooting |
| [10-testing/](10-testing/testing-guide.md) | Test suites, commands, and acceptance criteria mapping |
| [11-deployment/](11-deployment/deployment-guide.md) | Build, packaging, deployment guide, and production checklist |
| [12-project-status/](12-project-status/implementation-status.md) | Implementation matrix, known issues, and future roadmap |

## Role-Based Reading Paths

- **Management & Product**:
  1. [Project Overview](01-project/project-overview.md)
  2. [Business Model](01-project/business-model.md)
  3. [Implementation Status](12-project-status/implementation-status.md)
  4. [Future Roadmap](12-project-status/future-roadmap.md)
- **New Developers**:
  1. [Local Setup](09-development/local-setup.md)
  2. [System Architecture](03-architecture/system-architecture.md)
  3. [Repository Structure](03-architecture/repository-structure.md)
  4. [Coding Standards](09-development/coding-standards.md)
- **Frontend Developers**:
  1. [Frontend Architecture](03-architecture/frontend-architecture.md)
  2. [Design System](07-design/design-system.md)
  3. [User Experience](07-design/user-experience.md)
  4. [API Reference](06-api/api-reference.md)
- **Backend Developers**:
  1. [Backend Architecture](03-architecture/backend-architecture.md)
  2. [Database Design](05-database/database-design.md)
  3. [Security Architecture](08-security/security-architecture.md)
  4. [API Reference](06-api/api-reference.md)
- **QA Engineers**:
  1. [Testing Guide](10-testing/testing-guide.md)
  2. [Acceptance Criteria](10-testing/acceptance-criteria.md)
  3. [Known Issues](12-project-status/known-issues.md)
- **Deployment Engineers**:
  1. [Deployment Guide](11-deployment/deployment-guide.md)
  2. [Environment Variables](09-development/environment-variables.md)
  3. [Production Checklist](11-deployment/production-checklist.md)
  4. [Data Protection](08-security/data-protection.md)
