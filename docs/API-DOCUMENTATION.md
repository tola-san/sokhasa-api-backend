# Sokhasa Clinic — Backend API Specification

**Version:** 1.0  
**Status:** Draft — Pending Business Validation  
**Backend:** Java 25 + Spring Boot  
**Database:** PostgreSQL  
**Authentication:** Keycloak + Spring Security  
**API Style:** RESTful JSON  
**Base Path:** `/api/v1`

---

## 1. API Overview

### 1.1 Purpose

This draft describes proposed REST APIs for the Sokhasa Clinic website and clinic management system. The shared implementation contract is designated in [AGENTS.md](../AGENTS.md) as `docs/api-contract.md`. The proposed endpoints, payloads, and permissions in this draft require review before implementation.

The API supports:

- Authentication and authorization.
- CMS page management.
- Public website content.
- Doctor management.
- Doctor schedules and availability.
- Service management.
- Public appointment booking.
- Reception appointment management.
- Media management.
- Staff management.
- Clinic settings.
- Dashboard statistics.
- Telegram notifications.

### 1.2 Base URLs

| Environment | Base URL |
|---|---|
| Local | `http://localhost:8080/api/v1` |
| Development | `https://api-dev.example.com/api/v1` |
| Production | `https://api.example.com/api/v1` |

Replace example domains with the actual deployment domains.

### 1.3 API Conventions

- Use RESTful resource naming.
- Use JSON for request and response bodies.
- Use UUID for resource identifiers.
- Use ISO 8601 for date/time values.
- Use UTC for stored timestamps; present clinic appointment times in the clinic's configured timezone.
- Use pagination for list endpoints.
- Use standard HTTP status codes.
- Validate all request data on the backend.
- Use `/api/v1` for versioning.

#### Content-Type

```http
Content-Type: application/json
Accept: application/json
```

#### Authentication Header

Protected endpoints require a valid Keycloak access token:

```http
Authorization: Bearer <access_token>
```

Public endpoints do not require authentication.

---

## 2. Authentication & Authorization

### 2.1 Authentication Provider

Keycloak is responsible for:

- User authentication.
- Login.
- Logout.
- Access token issuance.
- Refresh token handling.
- User identity.
- Role assignment.

Spring Security validates JWT access tokens.

The application must not implement its own password authentication system.

### 2.2 Keycloak Configuration

| Configuration | Value |
|---|---|
| Realm | `sokhasa-clinic` |
| Frontend Client | `sokhasa-admin` |
| Backend API Audience | `sokhasa-api` |
| Authentication Flow | Authorization Code + PKCE |
| Token Format | JWT |
| Authorization | Role-based |

### 2.3 Roles

| Role | Description |
|---|---|
| ADMIN | Administrative access |
| RECEPTION | Appointment management access |

Use backend authorization checks for every protected operation.

Suggested Spring Security authorities:

```text
ROLE_ADMIN
ROLE_RECEPTION
```

Role mapping must be consistent with the actual Keycloak token structure.

### 2.4 Authentication APIs

#### GET `/auth/me`

Get the currently authenticated user.

**Authorization:** ADMIN, RECEPTION

Response `200 OK`:

```json
{
  "id": "uuid",
  "keycloakUserId": "keycloak-user-id",
  "username": "admin",
  "email": "admin@example.com",
  "roles": ["ADMIN"],
  "active": true
}
```

The response must not contain passwords, tokens, or private Keycloak credentials.

#### GET `/auth/permissions`

Get the current user's application permissions.

**Authorization:** ADMIN, RECEPTION

Response:

```json
{
  "roles": ["RECEPTION"],
  "permissions": [
    "appointment:read",
    "appointment:confirm",
    "appointment:reject",
    "appointment:reschedule",
    "appointment:assign-doctor"
  ]
}
```

Permissions should reflect the approved application authorization policy.

---

## 3. Common API Response Standards

### 3.1 Successful Single Resource

```json
{
  "data": {
    "id": "uuid",
    "name": "Example"
  }
}
```

### 3.2 Successful List Response

```json
{
  "data": [],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### 3.3 Error Response

Use a consistent error structure.

```json
{
  "type": "VALIDATION_ERROR",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/public/appointments",
  "timestamp": "2026-11-14T08:00:00Z",
  "errors": [
    {
      "field": "phone",
      "message": "Phone number is required."
    }
  ]
}
```

The implementation may use Spring ProblemDetail with a consistent extension structure.

### 3.4 HTTP Status Codes

| Status | Meaning |
|---|---|
| 200 | OK |
| 201 | Created |
| 204 | No Content |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict |
| 422 | Unprocessable Entity, if adopted consistently |
| 429 | Too Many Requests |
| 500 | Internal Server Error |
| 503 | Service Unavailable |

Use `409 Conflict` for booking conflicts and other resource-state conflicts.

---

## 4. CMS Page Management APIs

**Phase 03 · Approved**

Clinic Admins (`ADMIN`) use a Keycloak access token to create, edit, and publish localized pages. Visitors read published translations through the [public page API](#51-get-a-published-page-translation). Admins can [upload an image](#132-upload-an-image) for use in a translation.

### 4.1 Routes

| Method and path | Request | Success |
|---|---|---|
| `GET /api/v1/admin/pages` | List filters | `200` paginated page summaries |
| `POST /api/v1/admin/pages` | Page fields | `201` page detail and `Location` header |
| `GET /api/v1/admin/pages/{id}` | Page UUID | `200` page detail and `ETag` |
| `PUT /api/v1/admin/pages/{id}` | Page fields and `If-Match` | `200` updated page detail |
| `PATCH /api/v1/admin/pages/{id}/publish` | `languageCodes` and `If-Match` | `200` page detail with updated publication metadata |
| `PATCH /api/v1/admin/pages/{id}/unpublish` | `If-Match`, empty body | `200` page detail with cleared publication metadata |
| `DELETE /api/v1/admin/pages/{id}` | `If-Match` | `204` with an empty body |

Use the [common response formats](#3-common-api-response-standards). Each `201` or `200` admin page-detail response includes the current strong `ETag`. The collection has no edit token. Admin responses use `Cache-Control: no-store`.

### 4.2 Page and translation fields

| Field | JSON type | Rules |
|---|---|---|
| `id` | UUID string | Server-generated. Admin resource identifier. |
| `slug` | String | Required on create and update. Unique, 1–80 characters, lowercase ASCII words and digits joined by single hyphens. Immutable after creation so public links stay stable. |
| `status` | String | Server-managed. `DRAFT` when there is no live publication and `PUBLISHED` otherwise. |
| `translations` | Array | Working copy. Contains one to three translations with unique `languageCode` values. |
| `publishedLanguageCodes` | String array | Languages in the current live publication. Empty for `DRAFT`. |
| `publishedAt` | Timestamp or null | Time of the current publication, cleared upon unpublish. |
| `createdAt`, `updatedAt` | Timestamp | Server-managed, ISO 8601 UTC. |

Each translation contains these fields.

| Field | JSON type | Rules |
|---|---|---|
| `languageCode` | String | Required. One of `km` (Khmer), `en` (English), or `zh` (Chinese). |
| `title` | String | Required, 1–160 characters after trimming. |
| `content` | String | Required HTML, up to 100,000 characters. Sanitize on the server with an explicit allowlist for rich text and safe link URLs. Reject content with no text after sanitization. |
| `image` | Object or null | Optional. Contains an uploaded image UUID in `mediaId` and localized `altText` (1–200 characters after trimming, required when an image is set). In a full draft save, use `null` or omit the field to remove the image. |

Only `slug` and `translations` are writable on create and update.

### 4.3 Create, retrieve and edit

Send `POST /api/v1/admin/pages` with an initial working copy.

```json
{
  "slug": "about-us",
  "translations": [
    {
      "languageCode": "en",
      "title": "About Us",
      "content": "<p>Learn about Sokhasa Clinic.</p>",
      "image": {
        "mediaId": "89f2b62b-e5a1-4fb3-bdb3-0fa912c86aef",
        "altText": "Entrance to Sokhasa Clinic"
      }
    }
  ]
}
```

The API creates a `DRAFT` and returns `201 Created` with `Location: /api/v1/admin/pages/6ff7ac02-6aca-446c-8f59-cd812afe23bc` and an `ETag`, such as `"1"`. The same response shape applies to page detail reads and edits.

```json
{
  "data": {
    "id": "6ff7ac02-6aca-446c-8f59-cd812afe23bc",
    "slug": "about-us",
    "status": "DRAFT",
    "translations": [
      {
        "languageCode": "en",
        "title": "About Us",
        "content": "<p>Learn about Sokhasa Clinic.</p>",
        "image": {
          "mediaId": "89f2b62b-e5a1-4fb3-bdb3-0fa912c86aef",
          "altText": "Entrance to Sokhasa Clinic"
        }
      }
    ],
    "publishedLanguageCodes": [],
    "publishedAt": null,
    "createdAt": "2026-11-14T08:00:00Z",
    "updatedAt": "2026-11-14T08:00:00Z"
  }
}
```

`PUT` replaces the complete working copy. Send the same slug and every translation to retain, including unchanged translations. Omitting a language removes it from the working copy. The editor should confirm that removal before saving. Saving a page with a live publication keeps it `PUBLISHED` and leaves the live version available until the next publish or unpublish action.

### 4.4 List and filter pages

| Query parameter | Default | Behavior |
|---|---|---|
| `page` | `0` | Zero-based page number. |
| `size` | `20` | Integer from 1 to 100. |
| `status` | Omitted | Filter by `DRAFT` or `PUBLISHED`. |
| `search` | Omitted | Case-insensitive substring match against the slug or any working translation title. Blank search applies no filter. |
| `sort` | `updatedAt,desc` | One of `updatedAt`, `createdAt`, or `slug`, followed by `asc` or `desc`. Break ties by `id` ascending. |

A list item has the same page metadata as the detail response, with translations reduced to `languageCode` and `title`. Pagination counts pages. A valid query with zero matches returns `200` with an empty `data` array.

### 4.5 Publish, unpublish and delete

`PATCH /api/v1/admin/pages/{id}/publish` takes one or more distinct language codes present in the working copy.

```json
{
  "languageCodes": ["en"]
}
```

Publish atomically replaces the live snapshot with the selected working translations, including each translation's image and alt text. The selection is the full set of languages to publish. If a previously live language is omitted, it stops being public. The editor should show which languages will be removed from the live page before submitting. Publishing returns the full admin page detail with `status: PUBLISHED`, the live language codes, and the publication timestamp.

Unpublish removes the live snapshot and returns the page to `DRAFT` while retaining its working translations. An unpublish request with the current `ETag` on an already-draft page succeeds without changing its timestamps or version. Delete is available for drafts. A published page must be unpublished before deletion.

### 4.6 Conflicts and validation

| HTTP status | Error type | Condition |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Invalid UUID, slug, filter, sort, body or language code. Also covers duplicate language codes in the same request, attempts to change the slug, malformed edit tokens, unknown image IDs, and missing image alt text. |
| `401` | `UNAUTHORIZED` | Missing or invalid token on an admin route. |
| `403` | `FORBIDDEN` | Valid token without CMS access. |
| `404` | `PAGE_NOT_FOUND` | Unknown page ID or no matching live public translation. |
| `409` | `SLUG_ALREADY_EXISTS` | Create conflicts with an existing slug. |
| `409` | `TRANSLATION_MISSING` | At least one supported language selected for publication is absent from the working copy. |
| `409` | `PAGE_IS_PUBLISHED` | Attempt to delete a published page. |
| `412` | `PAGE_VERSION_MISMATCH` | `If-Match` is stale. Reload the current page before reapplying changes. |
| `428` | `PAGE_VERSION_REQUIRED` | Required `If-Match` is missing. |

Use one strong `If-Match` token from the last page-detail response, for example `If-Match: "1"`. Version checks and changes to translations or publication occur in the same transaction. Enforce slug uniqueness and one working translation per page and language in PostgreSQL. Concurrent changes with the same version can produce at most one committed state change. Failed validation, publication and version checks leave both the working copy and live snapshot intact.

---

## 5. Public Website APIs

### 5.1 Get a published page translation

`GET /api/v1/public/pages/{slug}?lang=en` requires no authentication. Supply exactly one `lang` value from `km`, `en`, and `zh`. Missing or unsupported values return `400 VALIDATION_ERROR`. A missing page, an unpublished page, or an unavailable live language returns `404 PAGE_NOT_FOUND`. The website passes its active language. The API uses that language exactly.

HTTP `200 OK`

```json
{
  "data": {
    "slug": "about-us",
    "status": "PUBLISHED",
    "languageCode": "en",
    "title": "About Us",
    "content": "<p>Learn about Sokhasa Clinic.</p>",
    "image": {
      "url": "https://example.r2.cloudflarestorage.com/media/clinic-entrance.webp?X-Amz-Signature=example",
      "altText": "Entrance to Sokhasa Clinic"
    }
  }
}
```

Build the public DTO only from the live snapshot for the requested language. Return `image: null` when the translation has no published image. Public responses use `Cache-Control: no-store` initially. Configure the website fetch and route rendering to read fresh data so a previously published page stops rendering after unpublish.

### 5.2 CMS integration and acceptance cases

The first integration uses the existing public layout for a localized informational page and supplies its localized title and body from the CMS. The proposed homepage uses the same Pages API with the slug `home`. Next.js maps this slug to `/` and can render its title, intro, and optional image from the published translation. Homepage section and banner editing require their own scope decision. The existing preview rows can represent translations grouped under a single page ID and slug.

| Scenario | Expected result |
|---|---|
| Create an English working translation | Admin sees a draft. The public endpoint returns `404`. |
| Publish English and edit its title | The public endpoint serves the previously published English title until republish. |
| Publish only Khmer after English was live | The public endpoint serves Khmer and returns `404` for English. |
| Publish a supported language missing from the working copy | Admin receives `409`. The previous live publication is still readable. |
| Replace a draft image after publishing | The public page serves the previously published image until republish. |
| Unpublish and read the page again | Public lookup returns `404` for every language. Admin detail retains the working translations. |
| Attempt a write as a receptionist | Admin route returns `403`. |
| Two admins update from the same version | The stale edit receives `412` and the first committed edit remains saved. |

---

## 6. Doctor Management APIs

**Phase:** 05  
**Authorization:** ADMIN

### 6.1 Doctor Data Model

| Field | Type | Description |
|---|---|---|
| id | UUID | Doctor ID |
| name | String | Doctor name |
| profileImageId | UUID, nullable | Profile image |
| description | String, nullable | Profile description |
| status | Enum | ACTIVE / INACTIVE |
| createdAt | Timestamp | Creation time |
| updatedAt | Timestamp | Update time |

Additional fields such as specialty, credentials, and doctor translations must be confirmed with the clinic.

### 6.2 API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/admin/doctors` | List doctors |
| POST | `/admin/doctors` | Create doctor |
| GET | `/admin/doctors/{id}` | Get doctor |
| PUT | `/admin/doctors/{id}` | Update doctor |
| PATCH | `/admin/doctors/{id}/activate` | Activate doctor |
| PATCH | `/admin/doctors/{id}/deactivate` | Deactivate doctor |
| DELETE | `/admin/doctors/{id}` | Delete doctor, if safe |
| GET | `/public/doctors` | List public doctors |
| GET | `/public/doctors/{id}` | Get public doctor |

### 6.3 Create Doctor

#### POST `/admin/doctors`

Request:

```json
{
  "name": "Dr. Example",
  "description": "Doctor profile description",
  "status": "ACTIVE"
}
```

Response:

```json
{
  "data": {
    "id": "uuid",
    "name": "Dr. Example",
    "status": "ACTIVE"
  }
}
```

---

## 7. Doctor Schedule & Availability APIs

**Phase:** 05  
**Authorization:** ADMIN for management; public availability is public.

### 7.1 Schedule Management

| Method | Endpoint | Description |
|---|---|---|
| GET | `/admin/doctors/{doctorId}/schedules` | List schedules |
| POST | `/admin/doctors/{doctorId}/schedules` | Create schedule |
| PUT | `/admin/doctors/{doctorId}/schedules/{scheduleId}` | Update schedule |
| DELETE | `/admin/doctors/{doctorId}/schedules/{scheduleId}` | Delete schedule |

Example schedule:

```json
{
  "dayOfWeek": "MONDAY",
  "startTime": "08:00",
  "endTime": "17:00",
  "effectiveFrom": "2026-11-16",
  "effectiveUntil": null
}
```

Break periods, multiple working shifts, and exceptions should be supported if confirmed by clinic operations.

### 7.2 Check Doctor Availability

#### GET `/public/appointments/availability`

Query parameters:

| Parameter | Type | Required |
|---|---|---|
| serviceId | UUID | Yes |
| doctorId | UUID | No |
| date | LocalDate | Yes |

Example:

```http
GET /api/v1/public/appointments/availability?serviceId=uuid&doctorId=uuid&date=2026-11-16
```

Response:

```json
{
  "data": {
    "date": "2026-11-16",
    "serviceId": "uuid",
    "doctorId": "uuid",
    "slots": [
      {
        "startTime": "08:00",
        "endTime": "08:30",
        "available": true
      },
      {
        "startTime": "08:30",
        "endTime": "09:00",
        "available": false
      }
    ]
  }
}
```

Rules:

- Consider doctor working hours.
- Consider service duration.
- Consider breaks and unavailable periods.
- Consider existing confirmed appointments.
- Consider appointment buffer rules if approved.
- Do not treat an availability response as a guaranteed reservation.
- Revalidate availability when the appointment is submitted.

If the doctor is not specified, return available options according to the approved booking policy.

---

## 8. Service Management APIs

**Phase:** 06  
**Authorization:** ADMIN

### 8.1 Service Data Model

#### Service

| Field | Type | Description |
|---|---|---|
| id | UUID | Service ID |
| durationMinutes | Integer | Appointment duration |
| status | Enum | ACTIVE / INACTIVE |
| createdAt | Timestamp | Creation time |
| updatedAt | Timestamp | Update time |

#### ServiceTranslation

| Field | Type | Description |
|---|---|---|
| id | UUID | Translation ID |
| serviceId | UUID | Service relationship |
| languageCode | String | km / en / zh |
| name | String | Service name |
| description | String | Service description |

### 8.2 Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/admin/services` | List services |
| POST | `/admin/services` | Create service |
| GET | `/admin/services/{id}` | Get service |
| PUT | `/admin/services/{id}` | Update service |
| PATCH | `/admin/services/{id}/activate` | Activate service |
| PATCH | `/admin/services/{id}/deactivate` | Deactivate service |
| DELETE | `/admin/services/{id}` | Delete service, if safe |
| GET | `/public/services` | List active services |
| GET | `/public/services/{id}` | Get public service |

### 8.3 Create Service

#### POST `/admin/services`

Request:

```json
{
  "durationMinutes": 30,
  "status": "ACTIVE",
  "translations": [
    {
      "languageCode": "en",
      "name": "General Consultation",
      "description": "General consultation service."
    },
    {
      "languageCode": "km",
      "name": "ពិនិត្យទូទៅ",
      "description": "សេវាពិនិត្យសុខភាពទូទៅ។"
    },
    {
      "languageCode": "zh",
      "name": "普通咨询",
      "description": "普通咨询服务。"
    }
  ]
}
```

---

## 9. Public Appointment Booking APIs

**Phase:** 07  
**Authorization:** Public

### 9.1 Appointment Data Model

#### Appointment

| Field | Type | Description |
|---|---|---|
| id | UUID | Appointment ID |
| reference | String | Public booking reference, if implemented |
| patientId | UUID | Patient relationship |
| serviceId | UUID | Service relationship |
| preferredDoctorId | UUID, nullable | Patient preference |
| assignedDoctorId | UUID, nullable | Reception assignment |
| appointmentDate | Date | Appointment date |
| startTime | Time | Start time |
| endTime | Time | End time |
| status | Enum | Appointment status |
| notes | Text, nullable | Patient notes |
| rejectionReason | Text, nullable | Rejection reason |
| createdAt | Timestamp | Creation time |
| updatedAt | Timestamp | Update time |

### 9.2 Create Appointment

#### POST `/public/appointments`

Request:

```json
{
  "patient": {
    "fullName": "John Doe",
    "phone": "+85512345678",
    "email": "john@example.com"
  },
  "serviceId": "service-uuid",
  "preferredDoctorId": "doctor-uuid",
  "appointmentDate": "2026-11-16",
  "startTime": "09:00",
  "notes": "Additional information"
}
```

Response `201 Created`:

```json
{
  "data": {
    "id": "appointment-uuid",
    "reference": "SK-20261116-0001",
    "appointmentDate": "2026-11-16",
    "startTime": "09:00",
    "endTime": "09:30",
    "status": "PENDING",
    "message": "Appointment request submitted successfully."
  }
}
```

The reference format is illustrative. The actual reference-generation strategy must guarantee uniqueness.

### 9.3 Booking Business Rules

- Patient name is required.
- Phone number is required.
- Service must be active.
- Doctor must be active if selected.
- Date and time must be valid.
- Appointment must follow clinic booking rules.
- End time is calculated by the backend.
- The backend must validate availability again before saving.
- Appointment is initially PENDING.
- Appointment creation and initial status history must be consistent.
- Duplicate submissions should be handled safely.
- Apply rate limiting and abuse protection to public booking endpoints.

#### Concurrency

The backend must prevent two requests from confirming overlapping appointments for the same doctor.

Use an appropriate transaction and concurrency strategy. A simple availability check followed by an unprotected insert is not sufficient.

---

## 10. Reception Appointment Management APIs

**Phase:** 08  
**Authorization:** ADMIN, RECEPTION according to permission policy.

### 10.1 List Appointments

#### GET `/admin/appointments`

Query parameters:

| Parameter | Type | Description |
|---|---|---|
| page | Integer | Page number |
| size | Integer | Page size |
| status | String | Appointment status |
| date | Date | Exact appointment date |
| dateFrom | Date | Start date |
| dateTo | Date | End date |
| doctorId | UUID | Assigned doctor |
| serviceId | UUID | Selected service |
| search | String | Patient name, phone or reference |
| sort | String | Sorting |

Example:

```http
GET /api/v1/admin/appointments?status=PENDING&page=0&size=20
```

Response:

```json
{
  "data": [
    {
      "id": "uuid",
      "reference": "SK-20261116-0001",
      "patient": {
        "fullName": "John Doe",
        "phone": "+85512345678"
      },
      "service": {
        "id": "uuid",
        "name": "General Consultation"
      },
      "preferredDoctor": {
        "id": "uuid",
        "name": "Dr. Example"
      },
      "assignedDoctor": null,
      "appointmentDate": "2026-11-16",
      "startTime": "09:00",
      "endTime": "09:30",
      "status": "PENDING"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

### 10.2 Get Appointment Details

#### GET `/admin/appointments/{id}`

Response includes:

- Patient details.
- Service information.
- Preferred doctor.
- Assigned doctor.
- Appointment date and time.
- Status.
- Notes.
- Status history.

### 10.3 Assign Doctor

#### PATCH `/admin/appointments/{id}/assign-doctor`

Request:

```json
{
  "doctorId": "doctor-uuid",
  "reason": "Assigning available doctor"
}
```

Rules:

- Doctor must be active.
- Doctor must be available.
- Appointment must be in an assignable state.
- Validate conflicts.
- Record the assignment change.

### 10.4 Confirm Appointment

#### PATCH `/admin/appointments/{id}/confirm`

Request:

```json
{
  "doctorId": "doctor-uuid",
  "notes": "Confirmed by reception"
}
```

Rules:

- Appointment must be in a confirmable state.
- Doctor assignment must be valid.
- Doctor availability must be checked again.
- Prevent overlapping confirmed appointments.
- Save the status history.
- Generate a notification event after the transaction commits.

### 10.5 Reject Appointment

#### PATCH `/admin/appointments/{id}/reject`

Request:

```json
{
  "reason": "Requested time is unavailable."
}
```

Rules:

- Rejection reason is required.
- Only authorized staff can reject.
- Save rejection reason.
- Save status history.
- Generate a notification event after the transaction commits.

### 10.6 Reschedule Appointment

#### PATCH `/admin/appointments/{id}/reschedule`

Request:

```json
{
  "doctorId": "doctor-uuid",
  "appointmentDate": "2026-11-17",
  "startTime": "10:00",
  "reason": "Patient requested a new time."
}
```

Rules:

- Validate new appointment date.
- Validate doctor availability.
- Calculate new end time.
- Prevent conflicting appointments.
- Preserve previous appointment details in history.
- Record who performed the rescheduling.
- Generate a notification event after the transaction commits.

### 10.7 Appointment History

#### GET `/admin/appointments/{id}/history`

Response:

```json
{
  "data": [
    {
      "id": "uuid",
      "oldStatus": "PENDING",
      "newStatus": "CONFIRMED",
      "changedBy": {
        "id": "uuid",
        "username": "reception01"
      },
      "reason": "Appointment confirmed.",
      "createdAt": "2026-11-14T08:30:00Z"
    }
  ]
}
```

---

## 11. Appointment Status Rules

The status model must be consistent across database, frontend, backend, and reporting.

Initial status:

```text
PENDING
```

Approved appointment transitions in AGENTS.md:

- `PENDING → CONFIRMED`
- `PENDING → REJECTED`

Approved visit transitions:

- `CONFIRMED → ARRIVED → IN_PROGRESS → COMPLETED`

Rescheduling and cancellation remain pending decisions (API-06 and API-07). Their draft endpoints and events do not introduce new approved status transitions.

Invalid status transitions must return `409 Conflict`.

---

## 12. Telegram Notification APIs & Integration

**Phase:** 09

Telegram is a backend integration, not a public application API.

### 12.1 Notification Events

| Event | Trigger |
|---|---|
| APPOINTMENT_CREATED | Patient submits booking |
| APPOINTMENT_CONFIRMED | Reception confirms |
| APPOINTMENT_REJECTED | Reception rejects |
| APPOINTMENT_RESCHEDULED | Reception reschedules |

### 12.2 Architecture

```text
Appointment Controller
        |
        v
Appointment Service
        |
        v
Database Transaction
        |
        +---- Appointment
        |
        +---- Status History
        |
        +---- Notification Outbox
                    |
                    v
             Background Worker
                    |
                    v
              Telegram Client
                    |
                    v
              Reception Group
```

### 12.3 Outbox Data Model

| Field | Type | Description |
|---|---|---|
| id | UUID | Event ID |
| eventType | String | Notification event |
| aggregateId | UUID | Appointment ID |
| payload | JSON | Event data |
| status | Enum | PENDING / PROCESSING / SENT / FAILED |
| attempts | Integer | Retry attempts |
| nextRetryAt | Timestamp | Retry time |
| createdAt | Timestamp | Created time |
| sentAt | Timestamp | Successful delivery time |

### 12.4 Notification Rules

- Telegram token must remain in backend environment variables.
- Do not expose Telegram integration endpoints publicly.
- Do not call Telegram inside the appointment transaction.
- Process notifications asynchronously.
- Retry transient failures.
- Prevent duplicate event processing.
- Log delivery results.
- Do not include unnecessary sensitive patient information.
- Appointment operations must succeed independently of Telegram availability.

### 12.5 Environment Variables

```env
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
TELEGRAM_RETRY_MAX_ATTEMPTS=5
```

---

## 13. Media Management APIs

**Phase 03 upload approved. Phase 10 media library proposed.**

Clinic Admins (`ADMIN`) need a valid bearer token for every media management route.

### 13.1 Endpoints

| Method and path | Phase | Purpose |
|---|---|---|
| `POST /api/v1/admin/media/upload` | 03 | Upload one image for use on a page. |
| `GET /api/v1/admin/media` | 10 | List uploaded images. |
| `GET /api/v1/admin/media/{id}` | 10 | Get image details. |
| `DELETE /api/v1/admin/media/{id}` | 10 | Delete an image if neither a draft nor a live translation references it. |

The initial CMS has nine API operations, including the eight page operations and one image upload.

### 13.2 Upload an image

Send a `multipart/form-data` request with one required part named `file`. The API accepts JPEG, PNG, and WebP up to 5 MiB and limits decoding to 40 million pixels. It returns `400` for a missing file or unreadable image, `413` for a file above the size limit, and `415` for an unsupported format.

```http
POST /api/v1/admin/media/upload
Authorization: Bearer <access_token>
Content-Type: multipart/form-data; boundary=<generated_boundary>
```

HTTP `201 Created`

```json
{
  "data": {
    "id": "89f2b62b-e5a1-4fb3-bdb3-0fa912c86aef",
    "fileName": "clinic-entrance.webp",
    "mimeType": "image/webp",
    "size": 102400
  }
}
```

Store the bytes in a private Cloudflare R2 bucket under a generated object key. Store the ID, object key, original filename, detected MIME type, and size in PostgreSQL. Return `201` only after both stores succeed. If the metadata write fails, remove the uploaded object or arrange cleanup if immediate deletion fails.

Use the returned ID as `image.mediaId` when creating or updating a translation. Provide alt text on the translation so it matches the page language. The public page API issues a short-lived signed GET URL for the image in the live snapshot. After unpublishing, the API stops issuing URLs for that translation. URLs already issued remain usable until they expire. [Cloudflare R2 presigned URLs](https://developers.cloudflare.com/r2/api/s3/presigned-urls/)

---

## 14. Staff Management APIs

**Phase:** 10  
**Authorization:** ADMIN

Keycloak is the identity provider.

### 14.1 Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/admin/staff` | List staff |
| POST | `/admin/staff` | Create staff |
| GET | `/admin/staff/{id}` | Get staff |
| PUT | `/admin/staff/{id}` | Update staff profile |
| PATCH | `/admin/staff/{id}/activate` | Activate staff |
| PATCH | `/admin/staff/{id}/deactivate` | Deactivate staff |
| PATCH | `/admin/staff/{id}/roles` | Update staff roles |

### 14.2 Create Staff

#### POST `/admin/staff`

Request:

```json
{
  "username": "reception01",
  "email": "reception@example.com",
  "firstName": "Reception",
  "lastName": "Staff",
  "roles": ["RECEPTION"]
}
```

The backend should use the Keycloak Admin API through a properly secured server-side integration.

Never store or return staff passwords.

---

## 15. Clinic Settings APIs

**Phase:** 10  
**Authorization:** ADMIN

### 15.1 Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/admin/settings/clinic` | Get clinic settings |
| PUT | `/admin/settings/clinic` | Update clinic settings |
| GET | `/admin/settings/working-hours` | Get clinic hours |
| PUT | `/admin/settings/working-hours` | Update clinic hours |
| GET | `/public/settings/clinic` | Get approved public clinic information |

### 15.2 Clinic Settings Example

```json
{
  "name": "Sokhasa Clinic",
  "phone": "+85512345678",
  "email": "info@example.com",
  "address": "Phnom Penh, Cambodia",
  "timezone": "Asia/Phnom_Penh",
  "supportedLanguages": ["km", "en", "zh"]
}
```

Public settings must contain only information approved for public display.

---

## 16. Dashboard APIs

**Phase:** 11  
**Authorization:** ADMIN, RECEPTION according to permissions.

### 16.1 Dashboard Overview

#### GET `/admin/dashboard/overview`

Query:

```http
GET /api/v1/admin/dashboard/overview?date=2026-11-14
```

Response:

```json
{
  "data": {
    "totalAppointments": 100,
    "pendingAppointments": 12,
    "confirmedAppointments": 70,
    "rejectedAppointments": 8,
    "todayAppointments": 10
  }
}
```

These are example values only. Production responses must use actual database data.

### 16.2 Upcoming Appointments

#### GET `/admin/dashboard/upcoming-appointments`

Query:

| Parameter | Type | Description |
|---|---|---|
| date | Date | Date filter |
| limit | Integer | Number of records |

### 16.3 Recent Activities

#### GET `/admin/dashboard/recent-activities`

Return approved operational activity records.

Do not expose unnecessary patient information in activity feeds.

---

## 17. API Authorization Matrix

| Module | Endpoint Group | ADMIN | RECEPTION | PUBLIC |
|---|---|---:|---:|---:|
| Authentication | `/auth/me` | Yes | Yes | No |
| CMS | `/admin/pages` | Yes | No by default | No |
| Public Pages | `/public/pages` | Yes | Yes | Yes |
| Doctors | `/admin/doctors` | Yes | No by default | No |
| Public Doctors | `/public/doctors` | Yes | Yes | Yes |
| Schedules | `/admin/doctors/{id}/schedules` | Yes | No by default | No |
| Availability | `/public/appointments/availability` | Yes | Yes | Yes |
| Services | `/admin/services` | Yes | No by default | No |
| Public Services | `/public/services` | Yes | Yes | Yes |
| Public Booking | `/public/appointments` | Yes | Yes | Yes |
| Appointment Management | `/admin/appointments` | Yes | Yes | No |
| Media | `/admin/media` | Yes | No by default | No |
| Staff | `/admin/staff` | Yes | No | No |
| Clinic Settings | `/admin/settings` | Yes | No by default | No |
| Dashboard | `/admin/dashboard` | Yes | Yes, limited | No |

The matrix is the proposed default. Final access must follow the clinic-approved permission policy.

---

## 18. Validation Requirements

### 18.1 General Validation

- Required fields cannot be empty.
- UUID values must be valid.
- String lengths must be limited.
- Enum values must be valid.
- Dates must use approved formats.
- Uploaded files must be validated.
- Unknown or unsupported languages must be rejected.

### 18.2 Appointment Validation

- Patient name is required.
- Phone number is required.
- Service must be active.
- Doctor must be active if assigned.
- Date must follow booking rules.
- Start time must fit the available schedule.
- End time is calculated by backend.
- Rejection reason is required.
- Rescheduling must validate availability.
- Confirmed doctor appointments must not overlap.

### 18.3 CMS Validation

- Slug must be unique.
- Language code must be supported.
- Translation language must be unique per page.
- Page content must be validated and safely rendered.
- Publishing must follow the approved translation policy.

---

## 19. API Security Requirements

- Validate Keycloak JWT signatures and claims.
- Validate token issuer and audience.
- Apply backend role-based authorization.
- Use HTTPS in production.
- Protect cookie-based authentication with CSRF defenses where applicable.
- Validate and sanitize input.
- Apply rate limiting to public booking endpoints.
- Avoid leaking patient information.
- Protect media upload endpoints.
- Never expose secrets in API responses or logs.
- Use least-privilege permissions for external integrations.
- Prevent IDOR vulnerabilities by checking authorization for every resource.

---

## 20. API Documentation & Testing

### 20.1 OpenAPI

Use Springdoc OpenAPI.

Suggested endpoints:

| Method | Endpoint | Description |
|---|---|---|
| GET | `/v3/api-docs` | OpenAPI JSON |
| GET | `/swagger-ui/index.html` | Swagger UI, if enabled |
| GET | `/scalar` | Scalar UI, if configured |

Documentation access in production should follow the clinic's security policy.

### 20.2 Required Tests

#### Authentication

- Valid token.
- Invalid token.
- Expired token.
- Missing token.
- Wrong role.

#### CMS

- Create page.
- Update page.
- Publish page.
- Unpublish page.
- Retrieve public published page.
- Prevent public draft access.

#### Appointment

- Valid booking.
- Invalid booking.
- Unavailable slot.
- Doctor assignment.
- Confirmation.
- Rejection.
- Rescheduling.
- Overlapping appointment prevention.
- Concurrent booking requests.
- Status history.

#### Telegram

- Notification event creation.
- Successful delivery.
- Failed delivery.
- Retry.
- Duplicate prevention.
- Telegram disabled.

---

## 21. API Development Order

Follow this sequence when implementing the APIs.

| Order | Module | Priority |
|---:|---|---|
| 1 | Authentication | P0 |
| 2 | CMS Page Management and image upload | P0 |
| 3 | Public Website | P0 |
| 4 | Doctor Management | P1 |
| 5 | Doctor Schedules & Availability | P1 |
| 6 | Service Management | P1 |
| 7 | Public Appointment Booking | P0 |
| 8 | Reception Appointment Management | P0 |
| 9 | Telegram Notifications | P1 |
| 10 | Media library browsing and deletion | P2 |
| 11 | Staff & Clinic Settings | P2 |
| 12 | Dashboard & Reporting | P0 |
| 13 | Integration Testing & Production Verification | P0 |

---

## 22. Pending API Decisions

The following items require clinic approval or technical confirmation.

| ID | Decision | Impact |
|---|---|---|
| API-01 | Multiple services in one appointment? | Booking request and database relationships |
| API-02 | Is preferred doctor optional? | Booking API |
| API-03 | Can a service be provided by multiple doctors? | Service/doctor relationship |
| API-04 | How are doctor breaks represented? | Schedule and availability APIs |
| API-05 | Is appointment duration fixed or service-based? | Availability and booking |
| API-06 | What is the cancellation workflow? | Appointment APIs |
| API-07 | Can confirmed appointments be rescheduled? | Status transition rules |
| API-08 | Is public booking status lookup required? | Public security |
| API-09 | Are patient notifications required? | Notification integration |
| API-10 | May admins publish a subset of languages, and what should the public API return for an unavailable translation? | CMS publishing and public reads |
| API-11 | What appointment time zone should be used? | Date/time handling |
| API-12 | Should reception have access to dashboard analytics? | Authorization |
| API-13 | Approve the working-copy and live-snapshot model, including replacement of the live language set on republish? | CMS editing and publishing |
| API-14 | Which existing website routes should use CMS content, and should homepage sections or banners be editable? | Next.js integration and CMS scope |

Do not invent business decisions. Record approved decisions and update this specification before changing implementation contracts.

---

## 23. Definition of API Completion

The API implementation is complete when:

- All approved endpoints are implemented.
- Request and response contracts match this specification.
- Authentication and authorization work correctly.
- Validation and error handling are consistent.
- Database transactions preserve business consistency.
- Appointment conflicts are prevented.
- CMS draft content remains private.
- Telegram notification delivery is reliable.
- API tests pass.
- OpenAPI documentation is updated.
- Frontend integration is verified.

---

**End of API Documentation**
