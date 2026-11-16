# Sokhasa Clinic API

Spring Boot REST API for Sokhasa Longevity Clinic & Maternity. The application uses Java 25, the Maven Wrapper, PostgreSQL 17, Flyway, Spring Data JPA, and Spring Security. The Next.js client lives in the [clinic-sokhasa repository](https://github.com/tola-san/sokhasa).

## Run locally

Install Git and Docker with Compose. For Maven on your machine, install JDK 25 and point `JAVA_HOME` to it.

```sh
git clone https://github.com/tola-san/sokhasa-api-backend.git
cd sokhasa-api-backend
docker compose up -d --build
```

The API listens on `http://localhost:8080` and applies Flyway migrations to the `clinic` schema at startup. PostgreSQL is mapped to `127.0.0.1:15432` for local development.

```sh
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/api/v1/clinic/info
```

Use `docker compose logs -f backend` to inspect startup and `docker compose down` to stop the stack. The PostgreSQL volume persists across restarts.

### Run with Maven

Start only the database with `docker compose up -d postgres`. If the Compose backend is running, stop it with `docker compose stop backend` to free port 8080. The `local` profile reads `.env` from the repository root.

PowerShell

```powershell
Copy-Item .env.example .env
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

macOS or Linux

```sh
cp .env.example .env
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

If port 15432 is in use, set `CLINIC_POSTGRES_HOST_PORT` before starting Compose and update the port in `SPRING_DATASOURCE_URL` in `.env`. The values in `.env.example` are for local development.

## API and authentication

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/api/v1/health` | Public application and database health check. |
| GET | `/api/v1/clinic/info` | Public clinic name and welcome message. |

The CMS admin paths are covered by a security filter for the Keycloak `ADMIN` realm role. The CMS, booking, and visit APIs are proposals in the draft documentation.

For staff JWT validation, configure a reachable Keycloak issuer with `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`. The `local` profile also accepts `KEYCLOAK_ISSUER_URI` in `.env` and defaults to `http://localhost:8081/realms/sokhasa-clinic`. Tokens must include `sokhasa-api` in `aud` and carry staff roles in `realm_access.roles`. Keycloak runs separately from this repository.

For a frontend running on your machine, set `BACKEND_BASE_URL=http://localhost:8080` in `clinic-frontend/.env.local`. For a deployed frontend, use the deployed API origin.

## Build and verify

Run `./mvnw verify` or `.\mvnw.cmd verify` on Windows. The Spring Boot integration test needs a running PostgreSQL instance. With the Compose database, set these variables in your shell before invoking Maven.

| Variable | Local value |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:15432/clinic` |
| `SPRING_DATASOURCE_USERNAME` | `clinic` |
| `SPRING_DATASOURCE_PASSWORD` | `localdev` |

[GitHub Actions](.github/workflows/ci.yml) runs Maven verification against PostgreSQL and checks the backend Docker build. For deployment, build from the repository root and supply the datasource credentials and Keycloak issuer through the hosting environment. `PORT` defaults to `8080`. Use `/api/v1/health` for the deployment health check.

## Repository layout

```text
src/main/java/                  Spring Boot application and API code
src/main/resources/             Application configuration and Flyway migrations
src/test/                       Application and security tests
.mvn/                           Maven Wrapper configuration
.github/workflows/              Backend CI
docs/                           API contract, OpenAPI, and draft endpoints
Dockerfile                      Backend image
docker-compose.yml              Local API and PostgreSQL
```

- [Approved API contract](docs/api-contract.md)
- [OpenAPI specification](docs/openapi.yaml)
- [Draft API documentation](docs/API-DOCUMENTATION.md)
- [Contributor and agent instructions](AGENTS.md)
