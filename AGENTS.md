# Sokhasa Clinic API

## Scope and authority

Read this file and any applicable directory-specific `AGENTS.md` before editing. Inspect the existing code and preserve unrelated work. This repository contains the backend for the C2G+ Clinic Appointment Booking System. Coordinate integration with the [clinic-sokhasa frontend](https://github.com/tola-san/sokhasa). The Team Lead has final authority over architecture, business rules, the API contract, and integration. Raise unresolved conflicts before implementing affected changes.

## Collaboration

- OpenAI Codex leads backend REST APIs, domain services, persistence, Spring Security and RBAC, appointment concurrency, backend tests, and API documentation.
- Google Antigravity leads the Next.js frontend and can contribute backend changes in coordination with Codex.
- Coordinate ownership before changing shared files or contracts. Use separate feature branches for independent work and pull requests for review. Preserve unfinished work from either agent.

## Backend architecture

- Use Java 25, Spring Boot 4, Maven Wrapper, Spring Security, Spring Data JPA, PostgreSQL, and Flyway. Dependency changes require an authorized implementation task.
- Follow the modular monolith and Controller → Service → Repository layering. Use request and response DTOs, Bean Validation, centralized exception handling, and service-level transaction boundaries. Keep JPA entities internal.
- Put schema changes in new versioned Flyway migrations under `src/main/resources/db/migration`. Preserve migrations after they have been applied.
- Keep credentials in the local or deployment environment. Commit only example configuration.

## Business rules

- Application roles are Patient / Guest, Receptionist (`RECEPTION`), and Clinic Admin (`ADMIN`). Doctors are schedulable clinic resources. The MVP has no doctor login or dashboard.
- New booking requests start at `PENDING` and require receptionist approval. The allowed appointment decisions are `PENDING → CONFIRMED` and `PENDING → REJECTED`.
- Visits advance through `CONFIRMED → ARRIVED → IN_PROGRESS → COMPLETED`.
- Prevent appointment double booking and schedule conflicts, including concurrent requests. Obtain Team Lead approval before adding roles, transitions, or approval rules.
- Services: the public list shows no prices. Each service detail page shows its description, preparation instructions, and related doctors.
- Doctors: public profiles, linked to the services they provide. Patients may name an optional, non-binding preferred doctor linked to the service; the receptionist assigns the doctor on confirm and may choose another ("Preferred doctor" (PD) revision, approved by the Team Lead on November 20, 2026).
- Packages: show the price, included services, exclusions, and preparation instructions.
- Booking: one service or one package creates one `PENDING` appointment for receptionist review.
- Package visits: one appointment per package, using a duration and buffer that the clinic defines for the package.

Contract pending: these rules are specified in the "Public catalogue" (PC) and "Packages and package booking" (PK) revisions of `docs/api-contract.md`. Both await Team Lead approval through PR review. Do not implement the affected endpoints, entities, or migrations until they are approved.

## API contract

Use `/api/v1` for all API routes. Follow [the approved contract](docs/api-contract.md) and its [OpenAPI specification](docs/openapi.yaml). Coordinate contract changes with frontend maintainers and obtain Team Lead direction for missing or ambiguous behavior. The [draft API documentation](docs/API-DOCUMENTATION.md) describes proposals requiring review before implementation.

## Verification and Git workflow

- Run `./mvnw verify` or `.\mvnw.cmd verify` against a local PostgreSQL instance before submitting backend changes. See [README.md](README.md) for setup.
- Use `develop` for integration, `feature/*` for features, `fix/*` for bug fixes, and `main` for production. Integrate through pull requests and never commit directly to `main`.
- For instruction-maintenance requests, edit only the requested `AGENTS.md` files. Keep implementation changes within the authorized scope.
