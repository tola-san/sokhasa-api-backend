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

The [CMS API](docs/api-contract.md#cms-approved-november-16-2026) supports admin page management and public reads of published translations. Admin routes require the Keycloak `ADMIN` realm role. Public booking and staff appointment routes are defined in the shared contract; new requests start as `PENDING` and require receptionist approval.

For staff JWT validation, configure a reachable Keycloak issuer with `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`. The `local` profile also accepts `KEYCLOAK_ISSUER_URI` in `.env` and defaults to `http://localhost:8081/realms/sokhasa-clinic`. Tokens must include `sokhasa-api` in `aud` and carry staff roles in `realm_access.roles`. Keycloak runs separately from this repository.

To enable CMS image uploads and signed image URLs, set `R2_ENDPOINT`, `R2_BUCKET`, `R2_ACCESS_KEY_ID`, and `R2_SECRET_ACCESS_KEY` on the backend service. Text-only pages can be created and read without R2.

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


## Exact-time booking

`GET /api/v1/public/booking-slots?date=2026-11-20&serviceId=<uuid>` lists available
starts in `Asia/Phnom_Penh` (GMT+7). Submit an exact request to
`POST /api/v1/public/appointments` using `preferredStartTime: "14:30"` instead of
`preferredPeriod`, along with the existing service/date/patient/language fields
and UUID `Idempotency-Key`. Requests still start as `PENDING` and require reception
approval. See the exact-time section of [the API contract](docs/api-contract.md).

Flyway V9 adds `clinic.doctor_booking_window`. It deliberately seeds no real
working hours. The clinic must supply verified doctor/service/date windows through
the Doctor Management API described below before slots can appear.
Split working windows around breaks and omit days off. A 30-minute grid defines
start choices; service duration plus turnover buffer determines occupied time.
An unresolved legacy period-only pending request blocks exact starts in its
same date/period until reception confirms or rejects it.

For example, after replacing the UUID placeholders with real active records and
choosing a clinic-approved future date, a reviewed migration can insert:

```sql
INSERT INTO clinic.doctor_booking_window
    (id, doctor_id, service_id, day, start_time, end_time)
VALUES
    ('<window-uuid>', '<doctor-uuid>', '<service-uuid>', '<approved-date>', '14:00', '17:00');
```

The example hours are illustrative. Referenced windows cannot be edited/deleted.
Use the Doctor Management API below to configure working windows. The public
frontend consumes the resulting exact-time availability.

Run all database tests against a disposable PostgreSQL instance with
`CLINIC_TEST_DATABASE_URL`, `CLINIC_TEST_DATABASE_USERNAME` and
`CLINIC_TEST_DATABASE_PASSWORD` set. On a database where Flyway first created its
history in `public`, set `SPRING_FLYWAY_DEFAULT_SCHEMA=public` so subsequent test
contexts find the same history. Use Java 25. On memory-constrained Windows hosts, a verified fallback is to set
`MAVEN_OPTS` to `-Xms32m -Xmx512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2
-Xss512k -XX:ReservedCodeCacheSize=64m -Djdk.attach.allowAttachSelf=true
-Dspring.test.context.cache.maxSize=1` (one line), then run
`.\mvnw.cmd -DforkCount=0 verify` to use a single JVM.


## Configure doctors and working windows through the API

Use an authenticated Keycloak token with the `ADMIN` role. Doctor resources have
no login. The existing `GET /api/v1/admin/doctors` remains the active-doctor picker
for reception; `GET /api/v1/admin/doctors/manage` includes inactive resources and
management fields. The interactive API reference is at `/api/v1/docs`.

1. Restart the backend to apply V10 and load the new endpoints.
2. Create a real doctor with `POST /api/v1/admin/doctors`:

```json
{ "fullName": "Dr. Sok", "status": "ACTIVE", "sortOrder": 0 }
```

3. Copy `data.id` from the response and choose an existing active service ID.
   Create a clinic-approved working window with
   `POST /api/v1/admin/doctors/{doctorId}/booking-windows`:

```json
{
  "serviceId": "<active-service-uuid>",
  "date": "2026-11-21",
  "startTime": "14:00",
  "endTime": "17:00"
}
```

The date/hours above are examples; replace them with real clinic data within the
configured booking horizon. Split morning/afternoon windows or breaks into
separate rows. Repeat for each working date/service. All times are GMT+7.

4. Check `GET /api/v1/public/booking-slots?date=2026-11-21&serviceId={serviceId}`,
   or choose that same service and date in the frontend. Available starts appear
   when the doctor is active, the service plus buffer fits, and period capacity
   and existing reservations permit them. Outstanding legacy period-only pending
   requests still block exact slots in that date/period.

Doctor and window detail/create/update responses carry a strong `ETag`, for
example `"0"`. Send that value as `If-Match` for PUT and DELETE. Use:

- `PUT /api/v1/admin/doctors/{doctorId}` with the full doctor body to edit or set
  `status: INACTIVE`. Deactivation fails while the doctor has outstanding bookings.
- `GET /api/v1/admin/doctors/{doctorId}/booking-windows?from=2026-11-21&to=2026-11-27`
  to inspect a range (at most 62 days).
- `GET`, `PUT`, or `DELETE /api/v1/admin/doctors/{doctorId}/booking-windows/{windowId}`
  to read/edit/remove an unused window. A used window is protected, including when
  reception changed a booking into that window. Reject or resolve appointments
  through the existing appointment workflow; schedule edits never cancel visits.

See the shared contract and OpenAPI for status codes, bodies and role requirements.
These endpoints configure live-slot booking; they do not switch to preferred-time
requests without doctor reservations.
