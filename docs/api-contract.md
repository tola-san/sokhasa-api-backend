# Sokhasa Clinic API Contract

## `GET /api/v1/clinic/info`

Public endpoint. No authentication or request body is required.

HTTP `200`, `application/json`

```json
{
  "name": "Clinic Sokhasa",
  "message": "Welcome to Clinic Sokhasa."
}
```

Both fields are required strings. See [OpenAPI specification](openapi.yaml) for the machine-readable contract and [API documentation](API-DOCUMENTATION.md) for proposed endpoints.

## `GET /api/v1/health`

Deployment health check. Returns HTTP `200` with `{"status":"UP"}` when the application and PostgreSQL are healthy. A `DOWN` or `OUT_OF_SERVICE` status returns HTTP `503`. The response omits component details.

## CMS homepage banner — approved November 16, 2026

> **Text fields superseded November 17, 2026.** The banner is now multilingual. The request
> parts, responses and text limits below are replaced by
> [Multilingual homepage banner](#multilingual-homepage-banner--approved-november-17-2026).
> The image rules, ETag handling and routes still apply.

Replaces the CMS page and media-upload contract approved earlier the same day. The
`/api/v1/admin/pages/**`, `/api/v1/admin/media/upload` and `/api/v1/public/pages/{slug}`
routes are removed. The banner is the first photo slide on the right of the public
homepage hero; the heading and introduction on the left are out of scope. English only.

| Method | Route | Access |
| --- | --- | --- |
| GET | /api/v1/admin/homepage/banner | Keycloak ADMIN role |
| PATCH | /api/v1/admin/homepage/banner | Keycloak ADMIN role, `If-Match` required |
| GET | /api/v1/public/homepage/banner | Public |

### `GET /api/v1/admin/homepage/banner`

HTTP `200` with a strong `ETag` header (`"<revision>"`) and `Cache-Control: no-store`.

```json
{
  "data": {
    "imageUrl": "https://<r2-signed-url>",
    "fileName": "clinic-team.webp",
    "altText": "Sokhasa Clinic staff standing outside the clinic",
    "heading": "Your Comfort, Our Priority",
    "description": "Experience gentle, personalized care designed for your peace of mind.",
    "publishedAt": "2026-11-16T09:30:00Z"
  }
}
```

Before the first publish every field is `null` and the ETag is `"0"`.

### `PATCH /api/v1/admin/homepage/banner`

`multipart/form-data` with optional parts `image`, `altText`, `heading` and `description`.
Saving publishes immediately; there is no draft.

- The first publish requires all four parts.
- Later requests accept any non-empty subset; omitted parts keep their current values.
- A replacement `image` requires `altText` in the same request.
- Text parts are trimmed and must be non-blank: `altText` 1–200, `heading` 1–80,
  `description` 1–200 characters.
- Images follow the CMS image rules: JPEG, PNG or WebP, up to 5 MiB and 40 million pixels.

The live banner switches only after the R2 upload and the PostgreSQL update both succeed.
A replaced image is removed from the database in the same transaction and its R2 object
is deleted afterwards; failed deletions are retried by the media cleanup job.

Returns HTTP `200` with the same body as GET and the new `ETag`.

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing or invalid fields, empty request, image without alt text, undecodable image |
| 412 | `VERSION_MISMATCH` | `If-Match` is not the current revision |
| 413 | `IMAGE_TOO_LARGE` | Image larger than 5 MiB |
| 415 | `UNSUPPORTED_IMAGE` | Image is not JPEG, PNG or WebP |
| 428 | `VERSION_REQUIRED` | `If-Match` is missing |
| 503 | `STORAGE_UNAVAILABLE` | R2 is unavailable or not configured |

### `GET /api/v1/public/homepage/banner`

HTTP `200` with `Cache-Control: no-store`:

```json
{
  "data": {
    "imageUrl": "https://<r2-signed-url>",
    "altText": "Sokhasa Clinic staff standing outside the clinic",
    "heading": "Your Comfort, Our Priority",
    "description": "Experience gentle, personalized care designed for your peace of mind."
  }
}
```

Returns HTTP `404` (`BANNER_NOT_FOUND`) before the first publish; the homepage then shows
its bundled first slide. `imageUrl` is a private R2 signed URL that expires after five
minutes, so the homepage requests the banner on every render and never caches it.

Errors use the CMS problem-details body (`type`, `title`, `status`, `detail`, `instance`,
`timestamp`, `errors`). See [OpenAPI specification](openapi.yaml).

## Multilingual homepage banner — approved November 17, 2026

> **Status:** Approved by the Team Lead on November 17, 2026, including decisions I18N-01 to
> I18N-05. This section replaces the banner text fields, request parts and responses of the
> English-only banner contract above. Image rules, ETags, error bodies and routes are unchanged.

### Why

The requirements say the public website supports Khmer, English and Chinese
(REQUIREMENT.md, "Languages"). The approved banner is English only, so Khmer and Chinese
visitors see an English heading on the homepage. The clinic's policy is that **every piece of
published content exists in all three languages**.

### Approved decisions

| ID | Decision | Answer |
| --- | --- | --- |
| I18N-01 | Which languages must exist before content is public? | All three: `km`, `en`, `zh`. |
| I18N-02 | What does the public API return for a missing language? | `404`. No silent fallback to another language. |
| I18N-03 | Default website language when the visitor's language is unknown | `km`. |
| I18N-04 | Is the image shared across languages? | Yes. One image; the alt text is translated. |
| I18N-05 | Text length limits | `heading` 1–120, `description` 1–300, `altText` 1–200 characters per language. Khmer needs more code points than English for the same text, so the English-only limits (80/200) are raised. |

### Language codes

Lowercase ISO 639-1 codes: `km` (Khmer), `en` (English), `zh` (Simplified Chinese). Any other
value, including `KM`, `en-US` or an empty value, returns `400 VALIDATION_ERROR`.

### Routes

The routes and their access rules do not change.

| Method | Route | Access |
| --- | --- | --- |
| GET | /api/v1/admin/homepage/banner | Keycloak ADMIN role |
| PATCH | /api/v1/admin/homepage/banner | Keycloak ADMIN role, `If-Match` required |
| GET | /api/v1/public/homepage/banner?lang={km\|en\|zh} | Public |

### `GET /api/v1/admin/homepage/banner`

HTTP `200` with a strong `ETag` (`"<revision>"`) and `Cache-Control: no-store`. Returns every
saved language so the editor can fill its KM / EN / ZH tabs.

```json
{
  "data": {
    "imageUrl": "https://<r2-signed-url>",
    "fileName": "clinic-team.webp",
    "publishedAt": "2026-11-16T09:30:00Z",
    "translations": [
      {
        "languageCode": "km",
        "heading": "ការថែទាំដោយក្តីយកចិត្តទុកដាក់",
        "description": "ទទួលបានការថែទាំទន់ភ្លន់ និងផ្ទាល់ខ្លួន សម្រាប់ភាពស្ងប់ចិត្តរបស់អ្នក។",
        "altText": "បុគ្គលិកគ្លីនិកសុខាសាឈរនៅខាងមុខគ្លីនិក"
      },
      {
        "languageCode": "en",
        "heading": "Your Comfort, Our Priority",
        "description": "Experience gentle, personalized care designed for your peace of mind.",
        "altText": "Sokhasa Clinic staff standing outside the clinic"
      },
      {
        "languageCode": "zh",
        "heading": "您的舒适，是我们的首要关注",
        "description": "体验温和、个性化的护理，让您安心无忧。",
        "altText": "索卡萨诊所员工站在诊所门外"
      }
    ],
    "missingLanguages": []
  }
}
```

- `translations` is always ordered `km`, `en`, `zh` and contains only saved languages.
- `missingLanguages` lists required languages with no saved text, in the same order.
- Before the first publish, `imageUrl`, `fileName` and `publishedAt` are `null`,
  `translations` is `[]`, `missingLanguages` is `["km","en","zh"]`, and the ETag is `"0"`.

### `PATCH /api/v1/admin/homepage/banner`

`multipart/form-data` with two optional parts. Saving publishes immediately; there is no draft.

| Part | Type | Content |
| --- | --- | --- |
| `image` | File | JPEG, PNG or WebP, up to 5 MiB and 40 million pixels (unchanged). |
| `translations` | `application/json` | Array of `{ languageCode, heading, description, altText }`. |

```json
[
  { "languageCode": "zh", "heading": "您的舒适，是我们的首要关注", "description": "体验温和、个性化的护理，让您安心无忧。", "altText": "索卡萨诊所员工站在诊所门外" }
]
```

Rules:

- At least one of `image` or `translations` is required.
- Each translation sent must contain all three text fields. Text is trimmed and must be
  non-blank and within the I18N-05 limits.
- A `languageCode` may appear only once per request.
- Languages not sent keep their saved text, so an admin can correct one language without
  resending the others.
- **A replacement `image` requires `translations` for all three languages in the same request**,
  because every alt text describes the image.
- **After the request is applied, all three languages must exist.** Otherwise the request
  returns `409 TRANSLATION_MISSING`, nothing is saved, and the live banner does not change.
  The first publish therefore needs the image and all three translations.

The live banner switches only after the R2 upload and the PostgreSQL update both succeed, in
one transaction, as before. Returns HTTP `200` with the same body as GET and the new `ETag`.

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Invalid or duplicate `languageCode`, missing or invalid text field, malformed `translations` JSON, empty request, undecodable image |
| 409 | `TRANSLATION_MISSING` | A required language would be missing after the request. `errors` lists each missing code as `{ "field": "translations", "languageCode": "zh" }`. |
| 412 | `VERSION_MISMATCH` | `If-Match` is not the current revision |
| 413 | `IMAGE_TOO_LARGE` | Image larger than 5 MiB |
| 415 | `UNSUPPORTED_IMAGE` | Image is not JPEG, PNG or WebP |
| 428 | `VERSION_REQUIRED` | `If-Match` is missing |
| 503 | `STORAGE_UNAVAILABLE` | R2 is unavailable or not configured |

### `GET /api/v1/public/homepage/banner?lang=km`

`lang` is required. The response contains exactly one language, flat, with
`Content-Language: km` and `Cache-Control: no-store`.

```json
{
  "data": {
    "languageCode": "km",
    "imageUrl": "https://<r2-signed-url>",
    "heading": "ការថែទាំដោយក្តីយកចិត្តទុកដាក់",
    "description": "ទទួលបានការថែទាំទន់ភ្លន់ និងផ្ទាល់ខ្លួន សម្រាប់ភាពស្ងប់ចិត្តរបស់អ្នក។",
    "altText": "បុគ្គលិកគ្លីនិកសុខាសាឈរនៅខាងមុខគ្លីនិក"
  }
}
```

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `lang` missing or not `km`, `en` or `zh` |
| 404 | `BANNER_NOT_FOUND` | Nothing published yet, or no saved text for the requested language |

On `404` the homepage shows its bundled first slide in the visitor's language.

### Migration of the existing banner

Flyway `V4` moves the current heading, description and alt text into the translation table as
`en`. Until an admin adds Khmer and Chinese:

- `?lang=en` keeps returning the current banner.
- `?lang=km` and `?lang=zh` return `404`, so those visitors see the bundled first slide.
- The admin GET reports `"missingLanguages": ["km","zh"]`, and the editor highlights them.

No English text is copied into the Khmer or Chinese rows as a placeholder.

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| First publish with image and all three translations | `200`; each `?lang=` returns its own text |
| First publish with image and only `en` | `409 TRANSLATION_MISSING` listing `km`, `zh`; public GET still `404` |
| Correct only the `zh` heading on a published banner | `200`; `km` and `en` unchanged |
| Replace the image with alt text in only one language | `400 VALIDATION_ERROR` |
| `?lang=fr`, `?lang=KM` or no `lang` | `400 VALIDATION_ERROR` |
| Same `languageCode` twice in one request | `400 VALIDATION_ERROR` |
| Khmer heading of 120 characters | `200` |
| Right after the V4 migration | `en` served; `km`/`zh` `404`; admin sees `missingLanguages: ["km","zh"]` |
| Two admins save from the same ETag | Second gets `412`; first remains live |

### Pattern for other translated content

Services, doctors and product catalogue text follow the same rules once their contracts are
written: admin routes return and accept a `translations` array; public routes take a
required `lang` and return one flat language with `Content-Language`; publishing requires all
three languages and returns `409 TRANSLATION_MISSING` otherwise.

## Booking periods and service durations — approved November 17, 2026

> **Status:** Approved by the Team Lead on November 17, 2026, including decisions BK-01 to
> BK-08, the capacity rule, the confirm rules and `PERIOD_FULL`. Covers P0 decisions 1 (book by
> time of day) and 2 (fixed duration per service, BD-04). Field names follow the draft
> [MVP API scope](API-DOCUMENTATION.md#mvp-api-scope). The period hours and capacities are
> placeholders, and the [open questions](#open-questions-for-the-team-lead) below are not yet
> decided.

### Why

Exact-slot booking needs every doctor's working hours, breaks and days off. In the MVP,
doctors are clinic resources with no login, so nobody maintains that data. Every request is
already reviewed by a receptionist, so the patient asks for a period and reception sets the
exact time and doctor when confirming. Services need a fixed length so the system can measure
period capacity and detect overlapping appointments.

### Proposed decisions

| ID | Decision | Proposal |
| --- | --- | --- |
| BK-01 | What does the patient choose? | Service, date and period (`MORNING` or `AFTERNOON`). No exact time. |
| BK-02 | Who sets the exact time and doctor? | The receptionist, in `PATCH …/confirm`. |
| BK-03 | Request and confirmed time stored separately? | Yes. `preferredDate` and `preferredPeriod` are never overwritten. `startAt`, `endAt` and `doctorId` are set on confirm. Exact-slot booking can be added later by filling `startAt` earlier. |
| BK-04 | Where are periods defined? | In a `booking_period` table, editable without a deploy: code, start, end and `capacityMinutes`. Seed values below are placeholders for the clinic to confirm. |
| BK-05 | Duration source (BD-04) | Each service has `durationMinutes` (5–480) and `bufferMinutes` (0–60) for cleaning or turnover. |
| BK-06 | Can reception change the duration? | Yes, for one appointment, with optional `durationMinutes` in the confirm body. The service value is the default. |
| BK-07 | Does a service edit change existing bookings? | No. The appointment copies `durationMinutes` and `bufferMinutes` when created. |
| BK-08 | Time zone | Store `timestamptz` (UTC). Interpret dates, periods and `startTime` in `Asia/Phnom_Penh`. |

### Periods (placeholders until the clinic confirms)

| `code` | `startTime` | `endTime` | `capacityMinutes` |
| --- | --- | --- | --- |
| `MORNING` | `08:00` | `12:00` | `480` |
| `AFTERNOON` | `13:30` | `17:00` | `420` |

`capacityMinutes` is the total appointment time the clinic accepts in that period, for example
4 hours × 2 doctors = 480.

### Capacity rule on booking

`POST /public/appointments` accepts a request only if the period still has room:

```
sum(duration + buffer) of PENDING and CONFIRMED appointments in that date and period
  + this service's (duration + buffer)  ≤  capacityMinutes
```

- A `PENDING` request counts against its `preferredPeriod`. A `CONFIRMED` one counts against
  the period containing its `startAt`. `REJECTED` requests do not count.
- The backend locks the date-and-period row (`SELECT … FOR UPDATE`) while counting, so
  simultaneous requests cannot overbook the period.
- A full period returns `409 PERIOD_FULL`. Nothing is created.

### Confirm rules

`PATCH /api/v1/admin/appointments/{id}/confirm`, as in the draft, with one optional field:

```json
{ "doctorId": "9a7e…", "date": "2026-11-20", "startTime": "09:30", "durationMinutes": 45 }
```

| Field | Rule |
| --- | --- |
| `doctorId` | Required; an active doctor |
| `date`, `startTime` | Required; local clinic time. May differ from the patient's preference. |
| `durationMinutes` | Optional; 5–480. Defaults to the copied service duration. |

- `endAt = startAt + durationMinutes`. The overlap check uses `endAt + bufferMinutes`.
- The whole appointment, including the buffer, must fit inside one period. Otherwise
  `400 VALIDATION_ERROR` on `startTime`.
- One doctor cannot have overlapping active appointments. The database enforces this, so two
  receptionists confirming at the same moment cannot both succeed:

  ```sql
  EXCLUDE USING gist (
    doctor_id WITH =,
    tstzrange(start_at, end_at + make_interval(mins => buffer_minutes)) WITH &&
  ) WHERE (status IN ('CONFIRMED', 'ARRIVED', 'IN_PROGRESS'))
  ```

  A violation returns `409 DOCTOR_UNAVAILABLE`, which already exists in the draft. The
  constraint needs the `btree_gist` extension, enabled in the same Flyway migration.

### New error code

| Status | Code | When |
| --- | --- | --- |
| 409 | `PERIOD_FULL` | The requested date and period have no remaining capacity |

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| Request a 30 + 10 minute service in a morning with 440 of 480 minutes used | `201`, `PENDING` |
| Same request when 450 of 480 minutes are used | `409 PERIOD_FULL`; nothing created |
| Two requests arrive at once for the last 40 minutes | One `201`, one `409 PERIOD_FULL` |
| Rejecting a pending request | Its minutes become available again |
| Confirm at `11:40` for a 30 + 10 minute service (ends `12:20`, after `MORNING` closes at `12:00`) | `400 VALIDATION_ERROR` |
| Confirm two overlapping appointments for one doctor at the same moment | One `200`, one `409 DOCTOR_UNAVAILABLE` |
| Confirm with `durationMinutes: 45` for a 30-minute service | `200`; `endAt` is `startAt + 45 min` |
| Admin changes a service from 30 to 45 minutes | Existing appointments keep 30 |
| Patient asked for `MORNING`, reception confirms at `14:00` | `200`; `preferredPeriod` stays `MORNING`; counts against `AFTERNOON` |

### Open questions for the Team Lead

1. Should the booking form show which periods are full before the patient submits? That needs
   one more public endpoint, for example
   `GET /api/v1/public/booking-periods?date=2026-11-20&serviceId=…`, returning each period with
   `available: true|false`. Without it, the patient learns only on submit (`409 PERIOD_FULL`).
2. What are the clinic's real period hours and capacity? The seed values above are placeholders.
3. Who edits periods and service durations? Proposal: `ADMIN` only. The editor screens come
   after the MVP; until then, seed data.

## Public service list — approved November 18, 2026

> **Status:** Approved by the Team Lead on November 18, 2026, including decisions SV-01 to SV-10. Covers
> endpoint 1 of the draft [MVP API scope](API-DOCUMENTATION.md#mvp-api-scope). Fields and tables
> follow [Service Management APIs](API-DOCUMENTATION.md#8-service-management-apis) and
> [Service tables](DATABASE-DESIGN.md#service-tables). The admin service endpoints stay
> **After MVP**.

### Why

The booking form needs the list of bookable services, each with its name, price and duration,
in the visitor's language. `POST /public/appointments` takes a `serviceId`, and the capacity rule
uses the service's `durationMinutes` and `bufferMinutes`, so services must come from the
database rather than the website's bundled data.

### Proposed decisions

| ID | Decision | Proposal |
| --- | --- | --- |
| SV-01 | Where does MVP service data come from? | A Flyway seed migration loads the current website services and categories into `service`, `service_translation`, `service_category` and `service_category_translation`. There are no admin service endpoints in the MVP; changes are new migrations. |
| SV-02 | Which services are listed? | `ACTIVE` services only. By I18N-01 an active service has all three languages, so every listed service exists in the requested language. |
| SV-03 | Missing language | Not possible for an active service. The list never falls back to another language (I18N-02). |
| SV-04 | Paging and filters | None. The catalogue is small, so the endpoint returns every active service in one response. |
| SV-05 | Order | Category `sortOrder`, then service `sortOrder`, then `slug`. |
| SV-06 | Price | Informational only, in US dollars. `priceUsd: null` means the website shows "ask the clinic". Booking never charges the patient. |
| SV-07 | Admin-only fields | `bufferMinutes`, `status` and `sortOrder` are never returned publicly. |

### Route

| Method | Route | Access |
| --- | --- | --- |
| GET | /api/v1/public/services?lang={km\|en\|zh} | Public |

### `GET /api/v1/public/services?lang=km`

`lang` is required and follows the [language code rules](#language-codes). HTTP `200` with
`Content-Language: km` and `Cache-Control: no-store`, because `imageUrl` is a signed R2 URL that
expires after five minutes, as for the banner.

```json
{
  "data": [
    {
      "id": "3f1c2a4e-8b7d-4c1a-9e2f-5d6b7a8c9d01",
      "slug": "colonoscopy",
      "categorySlug": "digestive-health",
      "categoryName": "ការថែទាំប្រព័ន្ធរំលាយអាហារ",
      "name": "ការឆ្លុះពោះវៀនធំ",
      "shortDescription": "ការពិនិត្យពោះវៀនធំដោយកាមេរ៉ា។",
      "durationMinutes": 60,
      "priceUsd": 450.00,
      "originalPriceUsd": null,
      "imageUrl": "https://<r2-signed-url>",
      "imageAltText": "រូបភាពកញ្ចប់សេវាឆ្លុះពោះវៀនធំ"
    }
  ]
}
```

| Field | Type | Rule |
| --- | --- | --- |
| `id` | UUID | Send as `serviceId` in `POST /public/appointments` |
| `slug` | string | Unique URL key |
| `categorySlug` | string | Category URL key |
| `categoryName` | string | Category name in `lang` |
| `name` | string | 1–120 characters, in `lang` |
| `shortDescription` | string | 1–300 characters, in `lang` |
| `durationMinutes` | integer | 5–480. Shown on the card, for example "1 hr" |
| `priceUsd` | number or `null` | Two decimal places, `≥ 0`. `null` = ask the clinic |
| `originalPriceUsd` | number or `null` | Price before a promotion, shown crossed out. Only set when `priceUsd` is set, and greater than it |
| `imageUrl` | string or `null` | Signed URL valid for five minutes. `null` when the service has no image |
| `imageAltText` | string or `null` | 1–200 characters, in `lang`. `null` exactly when `imageUrl` is `null` |

- `data` is always an array. With no active services it is `[]` with HTTP `200`, not `404`.
- Every field is present in every item; optional values are `null`, never omitted.

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `lang` missing or not `km`, `en` or `zh` |

Errors use the CMS problem-details body. See [OpenAPI specification](openapi.yaml).

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| `?lang=km` with three active and one inactive service | `200`; three items, Khmer text, `Content-Language: km` |
| `?lang=fr`, `?lang=KM` or no `lang` | `400 VALIDATION_ERROR` |
| No active services | `200` with `"data": []` |
| Service with no price | `priceUsd` and `originalPriceUsd` are `null` |
| Service with no image | `imageUrl` and `imageAltText` are `null` |
| Any response | No `bufferMinutes`, `status` or `sortOrder` field |
| Service deactivated by a migration | Disappears from the list; existing appointments keep it (`ON DELETE RESTRICT`) |

### Resolved questions

| ID | Question | Decision |
| --- | --- | --- |
| SV-08 | Category filter? | None. The booking form groups by `categorySlug` on the client. |
| SV-09 | Detail-page fields (overview, FAQs, procedure) | Stay as bundled website content. The API covers card and booking data only. |
| SV-10 | Seeded images | Not seeded. Seeded services have `imageUrl: null` and `imageAltText: null`; the website keeps its own images by `slug` until the service editor uploads them to R2. |
