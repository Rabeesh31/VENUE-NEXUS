# Contributing to VENUE NEXUS

Thank you for contributing to VENUE NEXUS. To maintain high code quality, consistency, and strict multi-tenant isolation, all team members must follow this guide.

---

## 1. Branching Strategy

The repository uses Git Flow with two permanent branches:
- `main`: Production-ready, stable releases only. Direct commits and pushes are strictly forbidden. Changes enter `main` only via release PRs from `develop`.
- `develop`: Primary integration branch for active sprint development.

### Branch Naming Conventions
Always branch off the latest `develop`:
- `feature/<phase-or-feature-name>`: New features (e.g., `feature/p1-monorepo-skeleton`, `feature/p3-auth-login`, `feature/p4-hierarchy-apis`, `feature/p5-resource-qr`).
- `fix/<issue-name>`: Bug fixes (e.g., `fix/token-expiry-check`).
- `chore/<task-name>`: Maintenance, tool configuration, or documentation updates (e.g., `chore/ci-gitleaks-rule`).
- `docs/<document-name>`: Contract or design updates (e.g., `docs/api-contract-v1`).

---

## 2. Commit Message Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:
- `feat:` A new feature or endpoint.
- `fix:` A bug fix.
- `chore:` Maintenance, dependency updates, build scripts.
- `docs:` Documentation and contract updates.
- `test:` Adding or updating tests.
- `ci:` CI workflow changes.

Keep commits atomic, focused, and small.

---

## 3. Pull Request & Review Process

1. **Target Branch:** All pull requests must target `develop`. Merges to `main` occur only at release gates via release PRs.
2. **Review Requirement:** Every pull request requires at least one peer review or Team Lead (Rabeesh R) approval before merging.
3. **Automated Verification:** All CI checks (backend compile, unit tests, frontend build, ArchUnit architecture checks, Gitleaks secret scan) must pass.
4. **Contract Synchronization Rule:** If a code change alters an API endpoint, error code, event envelope, or database schema, the corresponding contract file under `docs/` (`api-contract.md`, `event-contract.md`, `db-schema.md`, etc.) **must be updated in the exact same PR and commit**.
5. **Post-Merge Verification:** Repeat full integration tests immediately after merging into `develop` to ensure no regression occurred.

---

## 4. Architectural Non-Negotiables

1. **Controller -> Service -> Repository:** Controllers must never access repositories directly.
2. **Module Encapsulation:** Modules expose public service interfaces; internal entities and repositories remain package-private where appropriate.
3. **Tenant Isolation:** Tenant identity is never accepted from client request bodies, query params, or path params for tenant-owned entities. It must strictly derive from the validated security context (`TenantContext`).
4. **No Plain Unscoped findById:** Repository queries for tenant-owned data must always require `tenantId`.
5. **No Secrets in Git:** Never commit secrets, passwords, tokens, API keys, or raw QR values. Use `.env.example` placeholders only.
6. **No "Lettuce" References:** The Redis client name must not appear in `docs/architecture.md` or `docs/deployment.md`.
