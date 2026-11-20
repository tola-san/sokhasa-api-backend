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

## Staff Telegram booking notifications

Optional backend-only integration. Each new
booking queues one `APPOINTMENT_CREATED` summary for the configured private reception
group. It includes the reference, patient name and phone, English service name
(slug fallback), requested date/time or period, `Pending review`, and submission
time in `Asia/Phnom_Penh`. Messages contain no buttons, links, patient email or
medical notes. Receptionists review bookings separately in the authenticated dashboard.
No public endpoints, patient Telegram subscriptions or appointment statuses are added.

1. Create a clinic bot through Telegram's BotFather and add it to a private group
   limited to authorized reception staff. Ensure it can send messages.
2. Obtain that group's numeric `chat.id` through the Telegram Bot API, using a
   group command/update visible to the bot. Keep tokens out of source control and logs.
3. Set `TELEGRAM_ENABLED=true`, `TELEGRAM_BOT_TOKEN`, and `TELEGRAM_CHAT_ID` in the
   backend environment (or `.env` for the local profile/Compose). The group ID is
   negative. Invalid enabled configuration fails startup. The default is disabled.
4. Restart the backend to apply Flyway V12 and load configuration. Create a test
   booking using the existing booking API. Configure test credentials only when
   deliberately testing real Telegram delivery.

The existing appointment transaction saves the booking and its outbox job together.
An idempotent booking replay does not queue another job. With Telegram disabled,
bookings do not create jobs; enabling it does not backfill old bookings. Disabling
it pauses existing jobs without deleting them. A job snapshots its destination and
message when created; changing the configured group affects new jobs only.

Every five seconds the worker processes up to ten jobs. Claims use PostgreSQL
`FOR UPDATE SKIP LOCKED` with a two-minute lease; network calls run after the claim
transaction commits, with five-second connect and ten-second read timeouts.
Expired leases can be reclaimed and stale workers cannot mark a newer claim complete.
Transient network/5xx failures retry with exponential backoff starting at 30 seconds;
429 responses also honor Telegram's `retry_after` for that job. Permanent API errors
fail immediately. `TELEGRAM_RETRY_MAX_ATTEMPTS` defaults to five (range 1–20).
Exhausted jobs remain `FAILED` for operator inspection; no public retry endpoint exists.

Inspect delivery counts without selecting patient messages:

```sql
SELECT status, count(*) FROM clinic.notification_job GROUP BY status;
SELECT id, attempts, last_error_code FROM clinic.notification_job WHERE status = 'FAILED';
```

Logs include job IDs and safe error codes only. Database access to the outbox must
be restricted because messages include patient contact information. Telegram delivery
is at least once: an ambiguous network timeout or a crash after Telegram accepts a
message can result in a duplicate. The booking reference helps staff recognize it.
Telegram outages do not roll back an already saved booking; an outbox database write
failure rolls back the booking transaction so it cannot silently lose the notification.

Notification unit tests use a mock HTTP transport; PostgreSQL tests use the existing
`CLINIC_TEST_DATABASE_*` variables and never send real Telegram messages.

### Patient subscription storage foundation

Flyway V13 adds `clinic.telegram_subscription`, mapped by the internal
`notification.model.TelegramSubscription` entity and `TelegramSubscriptionRepository`.
It stores one booking-to-private-chat link, consent time, optional revocation time
and an optimistic-lock version. Chat IDs use `bigint`/Java `long`. A private chat can
subscribe to several bookings, but a booking has one immutable recipient; another
insert cannot silently replace that recipient. Revocation preserves the original
consent and first revocation timestamp. Repository active lookups exclude revoked rows.

This is persistence only. There is no subscription creation endpoint, bot-link
token flow, Telegram webhook, patient delivery or SSE. Existing staff notifications
continue to use `notification_job`. Patient notification behavior remains subject
to the separate decision required by AP-14 in the shared API contract. A future
approved linking service must verify booking ownership, consent and a private-chat
Telegram update before saving a subscription; a numeric chat ID is not proof of
ownership. Patient delivery must check active consent, including when retrying a job.

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
