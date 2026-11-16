---
name: spring-boot-engineer
description: Implement and maintain this clinic application's Spring Boot backend, including REST APIs, business logic, persistence, security, migrations, and concurrency-safe appointment scheduling. Use when working in clinic-backend or on its API contract.
---

# Spring Boot Engineer

Use this skill for backend implementation in the C2G+ Clinic Appointment Booking System. It is adapted from the [Spring Boot Engineer skill](https://jeffallan.github.io/claude-skills/skills/backend/spring-boot-engineer/) by Jeff Smolinski, with project-specific architecture and business rules taking precedence.

## Before changing code

1. Read the repository `AGENTS.md` and any applicable directory-level `AGENTS.md`.
2. Inspect the existing backend code, `pom.xml`, configuration, and migration conventions. Follow the versions and patterns already present; the approved stack is Java 25, Spring Boot 4, Maven, Spring Security, Spring Data JPA, PostgreSQL, and Flyway.
3. Read `/docs/api-contract.md` before changing API behavior. The backend owns the API implementation, routes use `/api/v1`, and the contract must be coordinated before changes. If the contract is missing or ambiguous, ask the Team Lead before implementing the affected behavior.
4. Preserve unrelated work and coordinate shared-file ownership with Antigravity. Do not overwrite unfinished work.

## Architecture

- Keep the backend a modular monolith with clear Controller → Service → Repository responsibilities.
- Use request and response DTOs with Bean Validation. Never return JPA entities from API endpoints.
- Put business rules in services, persistence in repositories, and HTTP concerns in controllers.
- Use constructor injection, explicit transaction boundaries, and centralized exception handling consistent with the existing codebase.
- Use Flyway migrations for schema changes. Keep database changes compatible with PostgreSQL and the repository's established migration conventions.
- Keep secrets and environment-specific configuration outside source-controlled configuration files.
- Do not add dependencies or change the approved stack without an implementation task authorizing it.

## Clinic rules

- Roles are Patient / Guest, Receptionist, and Clinic Admin. Doctors are clinic resources, not application users; the MVP has no Doctor login or dashboard.
- A new booking request starts as `PENDING` and requires receptionist approval.
- Appointment transitions are `PENDING → CONFIRMED` or `PENDING → REJECTED`.
- Visit transitions are `CONFIRMED → ARRIVED → IN_PROGRESS → COMPLETED`.
- Prevent double booking and schedule conflicts, including concurrent requests. Choose and document a persistence-level concurrency strategy that matches the existing schema and transaction model.
- Do not introduce roles, lifecycle transitions, approval rules, or API behavior that the approved contract and Team Lead have not authorized.

## Implementation workflow

1. Trace the existing feature path across controller, service, repository, DTOs, security, and migrations before editing.
2. Identify authorization, validation, transaction, and concurrency requirements before implementation.
3. Make the smallest coherent change that follows existing naming and package conventions.
4. For API changes, keep routes, request/response shapes, status codes, validation, and error behavior aligned with `/docs/api-contract.md`.
5. Add or update focused tests when the implementation task requests testing, covering success, validation, authorization, failure, and concurrent scheduling cases where relevant.
6. Run the checks requested by the task and report their results. Do not claim checks passed unless they were run.

## Avoid

- Field injection or business logic in controllers.
- Exposing persistence entities or internal exception details through the API.
- Bypassing validation, authorization, transaction boundaries, or concurrency protection.
- Inventing API contract details from frontend needs.
- Introducing reactive programming or microservices patterns into this modular monolith without explicit architectural approval.
- Editing frontend files unless the task explicitly requests frontend work; coordinate those changes with Antigravity.
