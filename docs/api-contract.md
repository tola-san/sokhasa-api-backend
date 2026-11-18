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

## Admin service editing (phase 1) — approved November 18, 2026

> **Status:** Approved by the Team Lead on November 18, 2026, including decisions SA-01 to SA-10. Phase 1 of
> [Service Management APIs](API-DOCUMENTATION.md#8-service-management-apis): staff edit the
> seeded services. Creating and deleting services, image upload and category management are
> phase 2 and stay **After MVP**. Follows the [multilingual banner](#multilingual-homepage-banner--approved-november-17-2026)
> conventions for translations, ETags and errors.

### Why

The six seeded services carry the website's placeholder prices and durations (SV-01). The clinic
needs to correct them, and the wording in each language, without a deploy. Durations and buffers
also drive booking capacity (BK-05), so reception needs them to be right before launch.

### Proposed decisions

| ID | Decision | Proposal |
| --- | --- | --- |
| SA-01 | Who can edit services? | Keycloak `ADMIN` only. `RECEPTION` gets `403`. Matches the booking periods open question 3. |
| SA-02 | What can be edited? | Text in each language, `durationMinutes`, `bufferMinutes`, `priceUsd`, `originalPriceUsd`, `sortOrder`, and `status` through activate / deactivate. |
| SA-03 | What cannot be edited in phase 1? | `slug` and category. The website matches images and detail content to services by `slug` (SV-09, SV-10), so changing it would break them. Images are phase 2. |
| SA-04 | Concurrent edits | Optimistic locking on `service.version`, sent as a strong `ETag` (`"<version>"`). Every write requires `If-Match`, as for the banner. |
| SA-05 | Partial translations | Languages not sent keep their saved text, so an admin can fix one language without resending the others. |
| SA-06 | Missing languages | An `ACTIVE` service must have all three languages (I18N-01). A write that would leave an active service without one, or an activation of a service missing one, returns `409 TRANSLATION_MISSING` and saves nothing. |
| SA-07 | Effect on existing bookings | None. Appointments copy duration and buffer when created (BK-07). |
| SA-08 | Deactivating a service | Allowed at any time. It disappears from the public list and booking form immediately; existing appointments keep it (`ON DELETE RESTRICT`). |

### Routes

| Method | Route | Access |
| --- | --- | --- |
| GET | /api/v1/admin/services | Keycloak ADMIN role |
| GET | /api/v1/admin/services/{id} | Keycloak ADMIN role |
| PUT | /api/v1/admin/services/{id} | Keycloak ADMIN role, `If-Match` required |
| PATCH | /api/v1/admin/services/{id}/activate | Keycloak ADMIN role, `If-Match` required |
| PATCH | /api/v1/admin/services/{id}/deactivate | Keycloak ADMIN role, `If-Match` required |

All responses use `Cache-Control: no-store`. An unknown or malformed `{id}` returns
`404 SERVICE_NOT_FOUND`.

### Admin service body

`GET /api/v1/admin/services/{id}` returns HTTP `200` with `ETag: "<version>"` and:

```json
{
  "data": {
    "id": "e105d306-5252-4b58-ac95-0b1308887001",
    "slug": "chronic-disease-management",
    "categorySlug": "general-internal-medicine",
    "categoryName": "General & Internal Medicine",
    "status": "ACTIVE",
    "durationMinutes": 30,
    "bufferMinutes": 0,
    "priceUsd": 35.00,
    "originalPriceUsd": null,
    "sortOrder": 1,
    "imageUrl": null,
    "updatedAt": "2026-11-18T06:49:50Z",
    "version": 0,
    "translations": [
      { "languageCode": "km", "name": "ការគ្រប់គ្រងជំងឺរ៉ាំរ៉ៃ និងវេជ្ជសាស្ត្រទូទៅ", "shortDescription": "…", "imageAltText": null },
      { "languageCode": "en", "name": "Chronic Disease Management & Internal Care", "shortDescription": "…", "imageAltText": null },
      { "languageCode": "zh", "name": "慢性病综合防治与内科长程管理", "shortDescription": "…", "imageAltText": null }
    ],
    "missingLanguages": []
  }
}
```

- `categoryName` is the English name, for the dashboard.
- `translations` is ordered `km`, `en`, `zh` and contains only saved languages; `missingLanguages`
  lists the others in the same order.
- `version` equals the `ETag` value without quotes, so the list can be edited without an extra GET.
- Every field is present; optional values are `null`.

### `GET /api/v1/admin/services`

HTTP `200` with `{ "data": [ <admin service body>, … ] }`: every service, active and inactive,
ordered as the public list (category `sortOrder`, service `sortOrder`, `slug`). No paging (SV-04).

### `PUT /api/v1/admin/services/{id}`

`application/json`, `If-Match` required. All scalar fields are required; `translations` is
optional.

```json
{
  "durationMinutes": 45,
  "bufferMinutes": 10,
  "priceUsd": 40.00,
  "originalPriceUsd": null,
  "sortOrder": 1,
  "translations": [
    { "languageCode": "en", "name": "Chronic Disease Management & Internal Care", "shortDescription": "Targeted protocols for hypertension and diabetes." }
  ]
}
```

| Field | Rule |
| --- | --- |
| `durationMinutes` | Integer, 5–480 |
| `bufferMinutes` | Integer, 0–60 |
| `priceUsd` | Number with at most two decimal places, `≥ 0`, or `null` ("ask the clinic") |
| `originalPriceUsd` | `null`, or greater than `priceUsd`; requires `priceUsd` |
| `sortOrder` | Integer, 0–9999 |
| `translations` | 0–3 items; each `languageCode` at most once and one of `km`, `en`, `zh` |
| `name` | Trimmed, 1–120 characters |
| `shortDescription` | Trimmed, 1–300 characters |
| `imageAltText` | Must be absent or `null` in phase 1, because services have no image yet |

Languages not sent keep their saved text (SA-05). Returns HTTP `200` with the admin service body
and the new `ETag`.

### `PATCH /api/v1/admin/services/{id}/activate` and `/deactivate`

No body; `If-Match` required. Returns HTTP `200` with the admin service body and the new `ETag`.
Activating an active service, or deactivating an inactive one, changes nothing and returns the
current body and `ETag`.

### Errors

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Invalid field, duplicate or unknown `languageCode`, malformed JSON, `imageAltText` sent |
| 401 | — | Missing or invalid bearer token |
| 403 | — | Caller lacks the `ADMIN` role |
| 404 | `SERVICE_NOT_FOUND` | Unknown or malformed `{id}` |
| 409 | `TRANSLATION_MISSING` | An active service would be missing a language, or activation of a service missing one. `errors` lists each code as `{ "field": "translations", "languageCode": "zh" }` |
| 412 | `VERSION_MISMATCH` | `If-Match` is not the current version |
| 428 | `VERSION_REQUIRED` | `If-Match` is missing |

Errors use the CMS problem-details body.

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| Admin changes a price from 35 to 40 | `200`; new `ETag`; public list shows `40.00` |
| Admin fixes only the `zh` name | `200`; `km` and `en` unchanged |
| Two admins save from the same `ETag` | First `200`; second `412 VERSION_MISMATCH` |
| `PUT` without `If-Match` | `428 VERSION_REQUIRED` |
| `originalPriceUsd` equal to `priceUsd` | `400 VALIDATION_ERROR` |
| `durationMinutes: 500` | `400 VALIDATION_ERROR` |
| Deactivate a service | `200`; it disappears from `GET /public/services` |
| Activate a service missing `zh` | `409 TRANSLATION_MISSING`; still `INACTIVE` |
| `RECEPTION` user calls any route | `403` |
| Change a duration that existing appointments use | `200`; existing appointments keep their copied duration |

### Resolved questions

| ID | Question | Decision |
| --- | --- | --- |
| SA-09 | Can `RECEPTION` view services in the dashboard? | No. All admin service routes are `ADMIN` only. |
| SA-10 | Audit history of price changes? | Not in phase 1. Only `updatedAt` is kept. |

## Service images (phase 2a) — approved November 18, 2026

> **Status:** Approved by the Team Lead on November 18, 2026, including decisions SI-01 to SI-07.
> Adds images to [admin service editing](#admin-service-editing-phase-1--approved-november-18-2026)
> and replaces SA-03's "images are phase 2" and the phase 1 rule that `imageAltText` must be
> absent. Image rules, storage and cleanup follow the [homepage banner](#cms-homepage-banner--approved-november-16-2026).

### Decisions

| ID | Decision | Answer |
| --- | --- | --- |
| SI-01 | Who can change images? | Keycloak `ADMIN` only, like every admin service route. |
| SI-02 | Image rules | JPEG, PNG or WebP, up to 5 MiB and 40 million pixels. Stored privately in R2; responses carry a signed URL valid for five minutes. |
| SI-03 | Alt text | One image per service, shared by all languages; the alt text is translated. Setting an image requires alt text in `km`, `en` and `zh`, 1–200 characters each. |
| SI-04 | Languages without text | A service must already have its name and description in all three languages before an image is set; otherwise `409 TRANSLATION_MISSING`. |
| SI-05 | Replacing or removing | The old file is removed from the database in the same transaction and deleted from R2 afterwards; failed deletions are retried by the media cleanup job. Removing an image also clears every alt text. |
| SI-06 | Editing alt text only | `PUT /api/v1/admin/services/{id}` accepts `imageAltText` (1–200 characters) when the service has an image. Absent or `null` keeps the saved alt text. Sending it for a service without an image is `400 VALIDATION_ERROR`. |
| SI-07 | Website | Service cards show the uploaded image when `imageUrl` is set, with `imageAltText` as its alt text; otherwise they keep the bundled picture by `slug` (SV-10). |

### Routes

| Method | Route | Access |
| --- | --- | --- |
| PUT | /api/v1/admin/services/{id}/image | Keycloak ADMIN role, `If-Match` required |
| DELETE | /api/v1/admin/services/{id}/image | Keycloak ADMIN role, `If-Match` required |

### `PUT /api/v1/admin/services/{id}/image`

`multipart/form-data` with two required parts:

| Part | Type | Content |
| --- | --- | --- |
| `image` | File | JPEG, PNG or WebP, up to 5 MiB and 40 million pixels |
| `altTexts` | `application/json` | Array of exactly three `{ "languageCode", "imageAltText" }`, one per `km`, `en`, `zh` |

The service switches to the new image only after the R2 upload and the PostgreSQL update both
succeed. Returns HTTP `200` with the admin service body (signed `imageUrl`, alt text in each
translation) and the new `ETag`.

### `DELETE /api/v1/admin/services/{id}/image`

No body. Returns HTTP `200` with the admin service body (`imageUrl: null`, every `imageAltText:
null`) and the new `ETag`. Deleting when there is no image changes nothing and returns the current
body and `ETag`.

### Errors

As for admin service editing, plus:

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing `image` or `altTexts`, malformed JSON, a language missing or repeated, alt text empty or over 200 characters, undecodable image |
| 409 | `TRANSLATION_MISSING` | The service lacks its name and description in a language (SI-04) |
| 413 | `IMAGE_TOO_LARGE` | Image larger than 5 MiB |
| 415 | `UNSUPPORTED_IMAGE` | Image is not JPEG, PNG or WebP |
| 503 | `STORAGE_UNAVAILABLE` | R2 is unavailable or not configured |

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| Upload a WebP with three alt texts | `200`; `imageUrl` set; public list shows the image and the alt text in each language |
| Upload with only `en` alt text | `400 VALIDATION_ERROR`; nothing stored |
| Replace an image | `200`; old R2 object deleted (or queued for cleanup) |
| Upload a 6 MiB file or a GIF | `413` or `415`; nothing changes |
| Remove the image | `200`; `imageUrl` and every `imageAltText` are `null`; the website shows the bundled picture again |
| Change only the `zh` alt text with `PUT` | `200`; image and other alt texts unchanged |
| Send `imageAltText` with `PUT` for a service without an image | `400 VALIDATION_ERROR` |
| Two admins upload from the same `ETag` | First `200`; second `412`; the second upload is discarded |

## Appointment booking and reception — approved November 18, 2026

> **Status:** Approved by the Team Lead on November 18, 2026, including decisions AP-01 to AP-16.
> Covers endpoints 3–12 of the draft [MVP API scope](API-DOCUMENTATION.md#mvp-api-scope), resolves
> open question 1 of [booking periods](#booking-periods-and-service-durations--approved-november-17-2026)
> (AP-11), BD-06 (AP-02) and BD-14 (AP-01). Builds on the approved booking-period, capacity and
> confirm rules (BK-01 to BK-08), which this section does not change.

### Why

The backend has the approved booking rules in its service layer: the appointment tables, the
capacity lock, the confirm overlap constraint and the status transitions. This section is the HTTP
contract that lets the patient booking form and the reception dashboard use them.

### Approved decisions

| ID | Decision | Answer |
| --- | --- | --- |
| AP-01 | Booking reference (BD-14) | Every request gets a unique, human-readable reference `SK-YYYYMMDD-NNNN`, for example `SK-20261120-0007`: the preferred date and a per-date counter. Shown to the patient and searchable by reception. |
| AP-02 | Booking window (BD-06) | `preferredDate` from today to today + 60 days, in clinic time. Stored as a setting so the clinic can change it without a deploy. |
| AP-03 | Duplicate submits | `POST /public/appointments` requires an `Idempotency-Key` header (a UUID from the form). A repeat with the same key and body returns the first response; the same key with a different body returns `409 IDEMPOTENCY_CONFLICT`. Keys are kept for 24 hours. |
| AP-04 | Patient identity | Patients are matched by phone number, normalised to E.164 (`012 345 678` → `+85512345678`). Each appointment keeps the name and phone as submitted, so a later booking never changes an earlier one. |
| AP-05 | Patient fields | `fullName` (1–120) and `phone` required; `email` optional (the current form collects it); `notes` optional, up to 500 characters; `language` required (`km`, `en`, `zh`) for later patient messages. |
| AP-06 | Preferred doctor | Not in the MVP. Reception chooses the doctor when confirming (BK-02), so the public doctor list (draft endpoint 2) is not needed for launch. |
| AP-07 | Who uses the admin routes | Keycloak `ADMIN` and `RECEPTION`, both with the same access to every route in this section. |
| AP-08 | Concurrent staff edits | Detail responses carry a strong `ETag` (`"<version>"`). Every `PATCH` requires `If-Match`; a stale value returns `412 VERSION_MISMATCH`, a missing one `428 VERSION_REQUIRED`. Same as banner and service editing. |
| AP-09 | Status history | Every status change writes one history row (who, when, from, to, reason) in the same transaction. Reception can read it per appointment. |
| AP-10 | Capacity clarification | `ARRIVED`, `IN_PROGRESS` and `COMPLETED` keep counting against their period, like `CONFIRMED`. The approved rule names only `PENDING` and `CONFIRMED`, but also says only `REJECTED` frees minutes; without this, a period reopens as patients check in. |
| AP-11 | Period availability before submit (open question 1) | Yes: `GET /public/booking-periods`, so the form can disable full periods instead of failing on submit with `409 PERIOD_FULL`. |
| AP-12 | Rate limit | 5 booking requests per 10 minutes per client IP; more returns `429 RATE_LIMITED`. |
| AP-13 | Not-found codes | Specific codes, as elsewhere in this contract: `APPOINTMENT_NOT_FOUND`, `SERVICE_NOT_FOUND`, `DOCTOR_NOT_FOUND`. |
| AP-14 | Out of scope for the MVP | Patient cancellation (BD-07), no-show, rescheduling, undoing a step, reception-entered bookings and patient notifications. Each needs its own decision; cancellation and no-show would add statuses. |
| AP-15 | Date range on the list | `from` and `to` (inclusive) on `GET /admin/appointments`, so the reception calendar loads a month or week in one request and the list's "Date range" filter works. |
| AP-16 | Doctors for confirm | `GET /admin/doctors` returns the active doctors (`id`, `fullName`), so reception can pick one in the confirm dialog. Read-only; the doctor editor stays after the MVP. |

### Routes

| # | Method | Route | Access |
| ---: | --- | --- | --- |
| 1 | GET | /api/v1/public/booking-periods?date=&serviceId= | Public |
| 2 | POST | /api/v1/public/appointments | Public, `Idempotency-Key` required |
| 3 | GET | /api/v1/admin/appointments | ADMIN, RECEPTION |
| 4 | GET | /api/v1/admin/appointments/{id} | ADMIN, RECEPTION |
| 5 | GET | /api/v1/admin/appointments/{id}/history | ADMIN, RECEPTION |
| 6 | PATCH | /api/v1/admin/appointments/{id}/confirm | ADMIN, RECEPTION, `If-Match` required |
| 7 | PATCH | /api/v1/admin/appointments/{id}/reject | ADMIN, RECEPTION, `If-Match` required |
| 8 | PATCH | /api/v1/admin/appointments/{id}/arrive | ADMIN, RECEPTION, `If-Match` required |
| 9 | PATCH | /api/v1/admin/appointments/{id}/start | ADMIN, RECEPTION, `If-Match` required |
| 10 | PATCH | /api/v1/admin/appointments/{id}/complete | ADMIN, RECEPTION, `If-Match` required |
| 11 | GET | /api/v1/admin/dashboard/summary | ADMIN, RECEPTION |
| 12 | GET | /api/v1/admin/doctors | ADMIN, RECEPTION |

Admin responses use `Cache-Control: no-store`. Times in responses are ISO 8601 with the clinic
offset, for example `2026-11-20T09:30:00+07:00` (BK-08).

### `GET /api/v1/public/booking-periods?date=2026-11-20&serviceId=…`

Both parameters are required. Returns each period with whether this service still fits:

```json
{
  "data": [
    { "code": "MORNING",   "startTime": "08:00", "endTime": "12:00", "available": true },
    { "code": "AFTERNOON", "startTime": "13:30", "endTime": "17:00", "available": false }
  ]
}
```

`available` is a hint only; the booking request re-checks capacity under the lock. A date outside
the booking window returns every period with `available: false`.

### `POST /api/v1/public/appointments`

```http
POST /api/v1/public/appointments
Idempotency-Key: 6f1c2a7e-0b5d-4b8e-9a51-3c0d2e7f9a10
```

```json
{
  "serviceId": "3f1c…",
  "preferredDate": "2026-11-20",
  "preferredPeriod": "MORNING",
  "language": "km",
  "patient": { "fullName": "Sok Dara", "phone": "012 345 678", "email": null },
  "notes": "First visit"
}
```

HTTP `201 Created`:

```json
{
  "data": {
    "id": "b2d4…",
    "reference": "SK-20261120-0007",
    "status": "PENDING",
    "preferredDate": "2026-11-20",
    "preferredPeriod": "MORNING"
  }
}
```

The response does not echo the patient's details.

### `GET /api/v1/admin/appointments`

| Parameter | Rule |
| --- | --- |
| `status` | Optional; one status |
| `date` | Optional; matches the confirmed date, or the preferred date while `PENDING` |
| `from`, `to` | Optional; an inclusive date range on the same date as `date` (AP-15). Not combined with `date`; at most 62 days |
| `q` | Optional; reference, patient name or phone (any format) |
| `page`, `size` | Default `0` and `20`; `size` up to `100` |

Ordered oldest first by `createdAt`, so the longest-waiting request is at the top.

```json
{
  "data": [
    {
      "id": "b2d4…",
      "reference": "SK-20261120-0007",
      "status": "PENDING",
      "patientName": "Sok Dara",
      "patientPhone": "+85512345678",
      "serviceName": "Chronic Disease Management",
      "preferredDate": "2026-11-20",
      "preferredPeriod": "MORNING",
      "doctorName": null,
      "startAt": null,
      "createdAt": "2026-11-18T10:05:00+07:00"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

`serviceName` is in English, the staff language.

### `GET /api/v1/admin/appointments/{id}`

HTTP `200` with a strong `ETag`. The list fields plus `serviceId`, `patientEmail`, `language`,
`notes`, `durationMinutes`, `bufferMinutes`, `doctorId`, `endAt` and `updatedAt`.

### `GET /api/v1/admin/appointments/{id}/history`

```json
{
  "data": [
    { "fromStatus": null, "toStatus": "PENDING", "actorType": "GUEST", "actorName": null, "reason": null, "changedAt": "2026-11-18T10:05:00+07:00" },
    { "fromStatus": "PENDING", "toStatus": "REJECTED", "actorType": "STAFF", "actorName": "Reception 1", "reason": "Fully booked", "changedAt": "2026-11-18T11:00:00+07:00" }
  ]
}
```

`actorName` is the staff member's Keycloak `name` (or `preferred_username`) at the time of the change.

### Status changes

| Route | From → to | Body |
| --- | --- | --- |
| `…/confirm` | `PENDING → CONFIRMED` | `{ "doctorId", "date", "startTime", "durationMinutes"? }`, as approved |
| `…/reject` | `PENDING → REJECTED` | `{ "reason": "…" }`, required, 1–500 characters |
| `…/arrive` | `CONFIRMED → ARRIVED` | None |
| `…/start` | `ARRIVED → IN_PROGRESS` | None |
| `…/complete` | `IN_PROGRESS → COMPLETED` | None |

Each returns HTTP `200` with the detail body and the new `ETag`. Checks run in this order: the
appointment exists (`404`), `If-Match` (`428` / `412`), the transition is allowed (`409
INVALID_TRANSITION`), then the body.

### `GET /api/v1/admin/dashboard/summary`

```json
{ "data": { "pending": 4, "today": { "CONFIRMED": 6, "ARRIVED": 1, "IN_PROGRESS": 1, "COMPLETED": 3 } } }
```

`today` is the current clinic date.

### `GET /api/v1/admin/doctors`

```json
{ "data": [ { "id": "9a7e…", "fullName": "Dr. Sok Vanna" } ] }
```

Active doctors only, in display order.

### Errors

Problem-details body as for the banner (`type`, `title`, `status`, `detail`, `instance`,
`timestamp`, `errors`).

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing or invalid fields, date outside the booking window, unknown period, invalid phone, confirm outside one period, reject without a reason |
| 404 | `APPOINTMENT_NOT_FOUND` | Unknown or malformed appointment `{id}` |
| 404 | `SERVICE_NOT_FOUND` | Unknown or inactive service |
| 404 | `DOCTOR_NOT_FOUND` | Unknown or inactive doctor on confirm |
| 409 | `PERIOD_FULL` | Approved; no capacity left in the date and period |
| 409 | `DOCTOR_UNAVAILABLE` | Approved; overlapping appointment for the doctor |
| 409 | `INVALID_TRANSITION` | The step is not allowed from the current status |
| 409 | `IDEMPOTENCY_CONFLICT` | `Idempotency-Key` reused with a different body |
| 412 | `VERSION_MISMATCH` | `If-Match` is not the current version |
| 428 | `VERSION_REQUIRED` | `If-Match` is missing |
| 428 | `IDEMPOTENCY_KEY_REQUIRED` | `Idempotency-Key` is missing on booking |
| 429 | `RATE_LIMITED` | Too many booking requests from one client |

### Acceptance cases

The approved booking-period acceptance cases still apply. In addition:

| Scenario | Expected result |
| --- | --- |
| Book with a new `Idempotency-Key` | `201`, `PENDING`, a new `reference` |
| Submit the same request twice with the same key | Second returns the same `201` body; one appointment exists |
| Same key, different body | `409 IDEMPOTENCY_CONFLICT` |
| Book 61 days ahead or yesterday | `400 VALIDATION_ERROR` on `preferredDate` |
| Book twice with `012 345 678` and `+855 12 345 678` | One patient; each appointment keeps its own submitted name |
| `RECEPTION` lists pending requests | `200`, oldest first |
| A guest calls an admin route | `401` |
| A full period on the booking form | `GET /public/booking-periods` shows it `available: false` |
| Two receptionists act on one request from the same `ETag` | First `200`; second `412` |
| `arrive` on a `PENDING` request | `409 INVALID_TRANSITION` |
| Full visit: confirm, arrive, start, complete | Each `200`; history has five rows in order |
| Sixth booking from one IP within 10 minutes | `429 RATE_LIMITED` |
