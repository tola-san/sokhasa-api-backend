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
| SV-06 | Price | Informational only, in US dollars. `priceUsd: null` means the website shows "ask the clinic". Booking never charges the patient. **Public price removed by PC-01 (November 19, 2026).** |
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
| SV-09 | Detail-page fields (overview, FAQs, procedure) | Stay as bundled website content. The API covers card and booking data only. **`description` and `preparation` moved to the API by PC-02 (November 19, 2026).** |
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
| AP-06 | Preferred doctor | Not in the MVP. Reception chooses the doctor when confirming (BK-02), so the public doctor list (draft endpoint 2) is not needed for launch. **Public doctor profiles added by PC-04 (November 19, 2026). Changed by PD-01 (November 20, 2026): patients may name an optional, non-binding preferred doctor; reception still assigns the doctor.** |
| AP-07 | Who uses the admin routes | Keycloak `ADMIN` and `RECEPTION`, both with the same access to every route in this section. |
| AP-08 | Concurrent staff edits | Detail responses carry a strong `ETag` (`"<version>"`). Every `PATCH` requires `If-Match`; a stale value returns `412 VERSION_MISMATCH`, a missing one `428 VERSION_REQUIRED`. Same as banner and service editing. |
| AP-09 | Status history | Every status change writes one history row (who, when, from, to, reason) in the same transaction. Reception can read it per appointment. |
| AP-10 | Capacity clarification | `ARRIVED`, `IN_PROGRESS` and `COMPLETED` keep counting against their period, like `CONFIRMED`. The approved rule names only `PENDING` and `CONFIRMED`, but also says only `REJECTED` frees minutes; without this, a period reopens as patients check in. |
| AP-11 | Period availability before submit (open question 1) | Yes: `GET /public/booking-periods`, so the form can disable full periods instead of failing on submit with `409 PERIOD_FULL`. |
| AP-12 | Rate limit | 5 booking requests per 10 minutes per client IP; more returns `429 RATE_LIMITED`. |
| AP-13 | Not-found codes | Specific codes, as elsewhere in this contract: `APPOINTMENT_NOT_FOUND`, `SERVICE_NOT_FOUND`, `DOCTOR_NOT_FOUND`. |
| AP-14 | Out of scope for the MVP | Patient cancellation (BD-07), no-show, rescheduling, undoing a step, reception-entered bookings and patient notifications. Each needs its own decision; cancellation and no-show would add statuses. *(Patient notifications: proposed by the patient Telegram updates revision, PT-01 to PT-12, November 20, 2026.)* |
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


## Exact-time booking - implementation revision, November 19, 2026

Requested by the user for the Spring Boot backend; prepared for coordinated PR
review. This extends BK-01/BK-02/AP-11 for exact requests. Legacy period requests
remain supported. Roles, receptionist approval and visit transitions stay unchanged.
See [design background](proposals/exact-time-booking.md).

### Public availability

Add `GET /api/v1/public/booking-slots?date=2026-11-20&serviceId=<uuid>`.
Both query parameters are required. Responses use `Cache-Control: no-store`.
An invalid/out-of-window date returns `400 VALIDATION_ERROR`; an inactive/unknown
service returns `404 SERVICE_NOT_FOUND`. The booking window uses clinic time.

```json
{
  "data": {
    "date": "2026-11-20",
    "timeZone": "Asia/Phnom_Penh",
    "slots": [
      { "startAt": "2026-11-20T14:00:00+07:00", "endAt": "2026-11-20T14:30:00+07:00" }
    ]
  }
}
```

Return available starts only, sorted and deduplicated across doctors. Starts are
on clinic-local `:00` or `:30` boundaries, strictly in the future. The grid does
not change service duration or buffer. `endAt` excludes buffer; availability
includes it. The example assumes a 30-minute service. Expose no doctor IDs,
patient information or internal reservation details.

Offer a start only if an active doctor has a verified service/date working window,
the full duration plus buffer fits in that window and one booking period, the doctor
has no overlapping pending exact reservation or confirmed/later visit, and period
capacity permits it. Legacy pending period-only requests across **all services**
block exact starts for the same date/period until reception resolves them. New
legacy requests can make exact availability disappear until resolved.
Missing schedules or no remaining availability returns `slots: []`.

### Public submission

Extend `POST /api/v1/public/appointments` with `preferredStartTime`, an optional
`HH:mm` string on the 30-minute grid, for example `"14:30"`. Require exactly one
non-null selector: `preferredPeriod` or `preferredStartTime`. Omit the unused
selector (null is also accepted). Empty period strings are invalid. The server
derives `preferredPeriod` for exact requests. `preferredDate` and all existing
patient, language, rate-limit, booking-window and idempotency rules still apply.

```json
{
  "serviceId": "3f1c2a7e-0b5d-4b8e-9a51-3c0d2e7f9a10",
  "preferredDate": "2026-11-20",
  "preferredStartTime": "14:30",
  "language": "en",
  "patient": { "fullName": "Sok Dara", "phone": "012 345 678", "email": null },
  "notes": null
}
```

Creation still returns `201` with `status: PENDING`, date, derived period, reference
and ID. Add nullable `preferredStartTime` to public creation and admin list/detail
responses: `"14:30"` for an exact request, `null` for legacy bookings. Requested
date/period/start are immutable. Confirmed doctor/times remain unset until approval.

Exact submission atomically reserves one eligible doctor interval including buffer.
Availability is a hint; repeat every check during submission. Add
`409 SLOT_UNAVAILABLE` for past starts, missing/free doctor windows or unresolved
legacy pending requests. Invalid grid/selectors or an interval that cannot fit
inside a period return `400 VALIDATION_ERROR`. Aggregate exhaustion retains
`409 PERIOD_FULL`. Failure creates no appointment or reservation. Idempotency
includes the selected start; a used key with a different time conflicts.

### Reception, concurrency and scheduling data

The confirm body and ETag requirements remain unchanged. For exact requests,
reception must choose a verified doctor/service/date window fitting duration
(including overrides) and buffer; missing windows return `409 SLOT_UNAVAILABLE`.
Reception can change time/doctor while preserving the patient's original request.
Overlaps return `409 DOCTOR_UNAVAILABLE`. Recheck target period capacity on confirm,
subtracting the existing pending booking when the target date/period is unchanged;
exhaustion returns `409 PERIOD_FULL`. Failed confirmation preserves its reservation.

One PostgreSQL exclusion constraint covers pending reservations and confirmed/later
visits, including completed visits and turnover buffers. Confirmation transfers the
reservation atomically; rejection releases it in the status/history transaction.
No expiration, cancellation or new lifecycle rules are introduced.
Booking locks its idempotency key before its date/period capacity key. Confirmation
locks the appointment, then source/target date-period keys in lexical order. Exact
allocation and confirmation hold shared locks on schedule and doctor rows.

`clinic.doctor_booking_window` stores `id`, `doctor_id`, `service_id`, `day`,
`start_time`, `end_time`, all times clinic-local. Each row is a verified window for
one doctor/service/date. Represent breaks with separate windows and absences with
no windows. Supply clinic-verified data through the Doctor and schedule management
API revision below (or reviewed seed migrations). Referenced
windows cannot be updated or deleted, including after rejection, for auditability.

V9 seeds no real doctor schedules. Do not use the reference date, selected 4:30 pm,
or its 2:00-5:00 pm times as clinic configuration. Current placeholder periods close
at 17:00, so positive-duration appointments cannot start at 17:00. Exact starts
appear only after verified data is configured and legacy pending requests resolved.

Roll out the backend additively; enable the frontend exact-time selector after the
backend and real schedule data are ready. Submit coordinated PRs in both repositories
and sync API contract/OpenAPI copies. Frontend UI implementation is a separate task.


## Doctor and schedule management - implementation revision, November 19, 2026

Requested by the user to configure doctors and expose live booking slots without SQL.
Prepared for coordinated PR review. Extends AP-16 and the exact-time revision;
roles and appointment transitions are unchanged. No doctor login is introduced.

All new routes below require Clinic Admin (`ADMIN`). Existing
`GET /api/v1/admin/doctors` remains available to ADMIN/RECEPTION and returns only
active `{id, fullName}` records without changing its response shape.

| Method | Route under `/api/v1/admin/doctors` | Purpose |
| --- | --- | --- |
| GET | `/manage` | Full doctor list, including inactive resources |
| POST | (base) | Create doctor |
| GET | `/{id}` | Doctor detail with ETag |
| PUT | `/{id}` | Replace name/status/display order; If-Match required |
| GET | `/{id}/booking-windows?from=&to=&serviceId=` | Working windows in inclusive required date range (maximum 62 days); optional service filter |
| POST | `/{id}/booking-windows` | Create dated service working window |
| GET | `/{id}/booking-windows/{windowId}` | Window detail with ETag |
| PUT | `/{id}/booking-windows/{windowId}` | Replace unused window; If-Match required |
| DELETE | `/{id}/booking-windows/{windowId}` | Delete unused window; If-Match required |

Doctor create/update body: `{ "fullName": "Dr. Sok", "status": "ACTIVE", "sortOrder": 0 }`.
All fields required; name trimmed, 1-120 characters; status ACTIVE/INACTIVE;
sortOrder integer >= 0. Response: `{data:{id,fullName,status,sortOrder,version,
createdAt,updatedAt}}`, with timestamps in GMT+7. `/manage` returns `{data:[...]}`
in display/name/id order. No doctor hard-delete route; set status INACTIVE.

Working-window body: `{ "serviceId": "<uuid>", "date": "2026-11-21",
"startTime": "14:00", "endTime": "17:00" }`. All fields required. Times are
strict HH:mm in Asia/Phnom_Penh and end must follow start. Date must be within
the configured public booking window, including today. Doctor and service must
be active for create/update. Window must fit at least one service duration plus
buffer. Overlapping windows for the same doctor/service/date are rejected;
windows for different services may overlap because appointment reservations
still enforce actual doctor availability. Represent breaks with separate windows.
Response adds `id`, `doctorId`, `version`; singular reads/writes return `{data:...}`,
lists `{data:[...]}`. Create returns 201 with Location and ETag; successful PUT
returns 200 with new ETag; DELETE returns 204. All responses use no-store.

PUT/DELETE require a strong numeric If-Match (`"0"`); missing header returns
428 VERSION_REQUIRED, malformed or stale value 412 VERSION_MISMATCH. Unknown
resources return 404 DOCTOR_NOT_FOUND / BOOKING_WINDOW_NOT_FOUND; inactive service
returns 404 SERVICE_NOT_FOUND. Bad input/ranges return 400 VALIDATION_ERROR.
409 BOOKING_WINDOW_OVERLAP rejects duplicate/overlapping windows.
409 SCHEDULE_IN_USE rejects changing/deleting any window referenced by a booking
(including rejected bookings, for auditability), or covering a confirmed/later
visit for this doctor/service. 409 DOCTOR_IN_USE blocks deactivation while the
doctor has a pending exact reservation, an arrived/in-progress visit, or a
confirmed/completed appointment whose occupied interval has not yet ended.
No appointment is moved or cancelled by these operations.

Doctor-row locks precede window-row locks. Booking/confirmation holds shared doctor
locks; management writes hold exclusive doctor locks, so deactivation and window
edits cannot race with reservations. Same-doctor window edits serialize overlap
checks; ETags prevent lost updates. Database triggers also protect used windows.
Real doctors/hours are provided by the clinic; no fictitious schedules are seeded.


## Public catalogue: prices, doctor profiles and service details - revision, November 19, 2026

Requested by the user to match the clinic's business rules. Prepared for coordinated
PR review in both repositories. Roles, appointment and visit transitions, and
receptionist approval are unchanged. No doctor login is introduced.

### Business rules

- Services are listed **without prices**. Prices belong to packages, which get their own
  contract later.
- Doctors have public profiles and are linked to the services they perform.
- A service detail page shows its description, preparation and related doctors.
- Patients still never choose a doctor. The receptionist assigns one on confirm (BK-02).
  Related doctors are information only. *(Changed by the preferred doctor revision, PD-01 and
  PD-02: patients may name a linked doctor as a non-binding preference.)*

### Decisions

| ID | Decision | Rule |
| --- | --- | --- |
| PC-01 | Public price | **Supersedes SV-06 for public responses.** `GET /public/services` and `GET /public/services/{slug}` return no `priceUsd` or `originalPriceUsd`. The booking form shows no price. The admin service fields (SA-02) stay, unchanged, for internal use. |
| PC-02 | Service detail | **Supersedes SV-09** for `description` and `preparation`. Overview text, FAQs and procedure steps stay as bundled website content by `slug`. |
| PC-03 | Detail text | Each service translation adds `description` (1–5000 characters, required for an `ACTIVE` service) and `preparation` (1–3000 characters, or `null` when nothing is needed). Plain text; blank lines separate paragraphs. |
| PC-04 | Public doctors | **Supersedes AP-06 where it says the public doctor list is not needed.** Only `ACTIVE` doctors with a `slug` and a profile in all three languages are public (I18N-01, I18N-02: no fallback). |
| PC-05 | Doctor profile | Each doctor adds a unique `slug` and, per language, `title` (for example "Gastroenterologist", 1–120 characters) and `bio` (1–3000 characters, or `null`). `fullName` stays one value for all languages. A doctor photo uses the service image rules (signed R2 URL, five minutes) and is phase 2; until then `imageUrl` and `imageAltText` are `null`. |
| PC-06 | Service–doctor link | Many-to-many table `service_doctor (service_id, doctor_id)`, both `ON DELETE RESTRICT`. Public responses include only links where both sides are public. A link does not create booking windows or restrict which doctor reception assigns. |
| PC-07 | Who edits | `ADMIN` only, through the routes below. |
| PC-08 | Order | Doctors: `sortOrder`, then `fullName`, then `id`. Services in a doctor profile: SV-05 order. |

### Routes

| Method | Route | Access |
| --- | --- | --- |
| GET | /api/v1/public/services?lang= | Public (changed: no price fields) |
| GET | /api/v1/public/services/{slug}?lang= | Public (new) |
| GET | /api/v1/public/doctors?lang= | Public (new) |
| GET | /api/v1/public/doctors/{slug}?lang= | Public (new) |
| PUT | /api/v1/admin/doctors/{id}/profile | ADMIN, `If-Match` required (new) |
| PUT | /api/v1/admin/services/{id}/doctors | ADMIN, `If-Match` required (new) |

All public routes require `lang`, return `Content-Language` and `Cache-Control: no-store`,
like the service list.

### `GET /api/v1/public/services?lang=km`

Unchanged except that `priceUsd` and `originalPriceUsd` are removed from every item.

### `GET /api/v1/public/services/{slug}?lang=en`

```json
{
  "data": {
    "id": "3f1c2a4e-8b7d-4c1a-9e2f-5d6b7a8c9d01",
    "slug": "colonoscopy",
    "categorySlug": "digestive-health",
    "categoryName": "Digestive Health",
    "name": "Colonoscopy",
    "shortDescription": "Camera examination of the large intestine.",
    "description": "A colonoscopy lets the doctor see the lining of the large intestine.",
    "preparation": "Eat a light diet the day before. Do not eat for 8 hours before the visit.",
    "durationMinutes": 60,
    "imageUrl": null,
    "imageAltText": null,
    "doctors": [
      {
        "slug": "dr-sok",
        "fullName": "Dr. Sok",
        "title": "Gastroenterologist",
        "imageUrl": null,
        "imageAltText": null
      }
    ]
  }
}
```

- `doctors` is always an array, `[]` when no public doctor is linked.
- Every field is present; optional values are `null`, never omitted.
- No `priceUsd`, `originalPriceUsd`, `bufferMinutes`, `status` or `sortOrder`.

### `GET /api/v1/public/doctors?lang=en`

```json
{
  "data": [
    {
      "slug": "dr-sok",
      "fullName": "Dr. Sok",
      "title": "Gastroenterologist",
      "imageUrl": null,
      "imageAltText": null,
      "serviceSlugs": ["colonoscopy", "gastroscopy"]
    }
  ]
}
```

`data` is `[]` when there are no public doctors. A doctor's `id` is never returned publicly.

### `GET /api/v1/public/doctors/{slug}?lang=en`

Returns the list item plus `bio` (string or `null`) and `services`, an array of
`{ slug, name, shortDescription }` in `lang` for the linked active services.

### `PUT /api/v1/admin/doctors/{id}/profile`

```json
{
  "slug": "dr-sok",
  "translations": [
    { "languageCode": "km", "title": "…", "bio": null },
    { "languageCode": "en", "title": "Gastroenterologist", "bio": "Dr. Sok has…" },
    { "languageCode": "zh", "title": "…", "bio": null }
  ]
}
```

- `slug`: lowercase letters, digits and hyphens, 1–80 characters, unique among doctors.
- `translations`: exactly one entry for each of `km`, `en` and `zh`.
- Response `200` with the profile, `serviceIds` and a new `ETag`; the doctor's `version` increases.

### `PUT /api/v1/admin/services/{id}/doctors`

Body `{ "doctorIds": ["9a7e…"] }` replaces the service's linked doctors. IDs must be
distinct existing doctors (`ACTIVE` or `INACTIVE`), at most 50. `[]` removes every link.
Response `200` with `{ "data": { "serviceId", "doctorIds" } }` and the service's new `ETag`.

Admin service text (`PUT /admin/services/{id}`, SA-02) adds `description` and `preparation`
to each translation, with the PC-03 rules. Activating a service requires `description`
in all three languages, otherwise `409 SERVICE_TRANSLATION_MISSING`.

### Errors

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Bad `lang`, slug or text length; duplicate or missing language; too many or duplicate `doctorIds` |
| 404 | `SERVICE_NOT_FOUND` | Unknown or inactive service slug (public); unknown service id (admin) |
| 404 | `DOCTOR_NOT_FOUND` | Unknown or non-public doctor slug (public); unknown doctor id (admin, including in `doctorIds`) |
| 409 | `DOCTOR_SLUG_TAKEN` | Another doctor has the slug |
| 412 / 428 | `VERSION_MISMATCH` / `VERSION_REQUIRED` | As for other admin edits |

### Migration

- Flyway adds `doctor.slug` (nullable, unique), `doctor_translation`, `service_doctor`, and
  `description` / `preparation` on `service_translation`.
- Seeded services get `description` from the website's current overview text so they stay
  active. `preparation` starts as `null`.
- Existing doctors stay non-public until an admin saves a profile.

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| `GET /public/services?lang=en` | `200`; no item has `priceUsd` or `originalPriceUsd` |
| `GET /public/services/colonoscopy?lang=km` with two linked public doctors and one inactive | `200`; two doctors, Khmer `title` |
| Service with no linked doctors | `200`; `"doctors": []` |
| Inactive service slug | `404 SERVICE_NOT_FOUND` |
| Doctor without a `zh` profile | Not in any list; detail returns `404 DOCTOR_NOT_FOUND` |
| Inactive doctor with a profile | Not public; links kept |
| Two doctors saved with the same slug | Second gets `409 DOCTOR_SLUG_TAKEN` |
| Link a doctor, then book the service | Booking is unchanged; reception may still assign any active doctor |
| Activate a service missing `description` in `en` | `409 SERVICE_TRANSLATION_MISSING` |

### Open questions for the Team Lead

1. Should the booking form say "price on request" or show nothing? This revision shows nothing.
2. Should related doctors limit which doctors reception may assign on confirm? This revision says no.


## Doctor profile details and photos - revision, November 19, 2026

> **Status:** Proposed. Awaits Team Lead approval through PR review in both repositories.
> Extends the [public catalogue](#public-catalogue-prices-doctor-profiles-and-service-details---revision-november-19-2026)
> revision (PC) so the website's doctor card can come from the API instead of bundled data.
> Replaces PC-05's "a doctor photo is phase 2". Photo rules, storage and cleanup follow
> [service images](#service-images-phase-2a--approved-november-18-2026) (SI-01 to SI-07).
> Roles, booking, appointment and visit transitions are unchanged. No doctor login is introduced.

### Why

The doctor card on the homepage and the doctor profile page show a photo, a role, specialty
tags, a bio, education, years of experience and a publication count. PC only covers the
slug, role (`title`) and bio. This revision adds the remaining fields and the photo.

### Field map

| Website card | API field | Stored in | Translated |
| --- | --- | --- | --- |
| Name | `fullName` | `doctor.full_name` (exists) | No (PC-05) |
| Profile link | `slug` | `doctor.slug` (PC-05) | No |
| Role | `title` | `doctor_translation.title` (PC-05) | Yes |
| Bio | `bio` | `doctor_translation.bio` (PC-05) | Yes |
| Education | `education` | `doctor_translation.education` (new) | Yes |
| Tags | `tags` | `doctor_translation.tags` (new) | Yes |
| Years of experience | `experienceYears` | derived from `doctor.practice_start_year` (new) | No |
| Publications | `publicationsCount` | `doctor.publications_count` (new) | No |
| Photo | `imageUrl` | `doctor.image_media_id` → `cms_media` (new) | No |
| Photo alt text | `imageAltText` | `doctor_translation.image_alt_text` (new) | Yes |

### Decisions

| ID | Decision | Rule |
| --- | --- | --- |
| DP-01 | Education | Per language, `education` is plain text, 1–500 characters, or `null`. |
| DP-02 | Tags | Per language, `tags` is an array of 0–5 distinct strings, 1–40 characters each, in display order. `[]` when there are none, never `null`. Tag counts may differ between languages. |
| DP-03 | Experience | The admin sets `practiceStartYear` (1950 to the current year in `Asia/Phnom_Penh`, or `null`). Public responses return `experienceYears = currentYear - practiceStartYear`, or `null` when the start year is not set. The number is never stored, so it stays correct each year. |
| DP-04 | Publications | `publicationsCount` is 0–10000 or `null`. The website hides the stat when it is `null`. |
| DP-05 | Specialties | No separate specialties field. A doctor's specialties are the linked services (PC-06). |
| DP-06 | Photo | One photo per doctor, shared by all languages, with alt text in each language. Image rules, storage, signed URL (five minutes), replacement and cleanup follow SI-02 and SI-05. Setting a photo requires alt text in `km`, `en` and `zh`, 1–200 characters each. |
| DP-07 | Photo needs a profile | A doctor must already have a saved profile (PC-05) in all three languages before a photo is set; otherwise `409 TRANSLATION_MISSING`. |
| DP-08 | Alt text only | The profile `PUT` accepts `imageAltText` per translation when the doctor has a photo. Absent or `null` keeps the saved alt text. Sending it for a doctor without a photo is `400 VALIDATION_ERROR`. Removing the photo clears every alt text. |
| DP-09 | Public list carries the card | **Changes PC's list item.** `GET /public/doctors` returns every card field (`bio`, `education`, `tags`, `experienceYears`, `publicationsCount`, photo), so the homepage card needs no request per doctor. The detail adds `services` only. The doctor summary inside a service detail stays as in PC. |
| DP-10 | Who edits | `ADMIN` only (PC-07). |
| DP-11 | Website fallback | When `imageUrl` is `null`, the website shows its bundled fallback doctor picture. |
| DP-12 | Reading a profile | `GET /api/v1/admin/doctors/{id}/profile` returns the admin profile body and `ETag`, so the editor can load it. `slug` is `null` and `translations` is `[]` until a profile is saved. |

### Routes

| Method | Route | Access |
| --- | --- | --- |
| GET | /api/v1/public/doctors?lang= | Public (changed: card fields, DP-09) |
| GET | /api/v1/public/doctors/{slug}?lang= | Public (changed: card fields) |
| GET | /api/v1/admin/doctors/{id}/profile | ADMIN (new, DP-12) |
| PUT | /api/v1/admin/doctors/{id}/profile | ADMIN, `If-Match` required (changed: new fields) |
| PUT | /api/v1/admin/doctors/{id}/image | ADMIN, `If-Match` required (new) |
| DELETE | /api/v1/admin/doctors/{id}/image | ADMIN, `If-Match` required (new) |

### `GET /api/v1/public/doctors?lang=en`

```json
{
  "data": [
    {
      "slug": "dr-sok",
      "fullName": "Dr. Sok",
      "title": "Gastroenterologist",
      "bio": "Dr. Sok has fifteen years of experience in digestive health.",
      "education": "MD, University of Health Sciences, Phnom Penh",
      "tags": ["Endoscopy", "Digestive health"],
      "experienceYears": 16,
      "publicationsCount": 12,
      "imageUrl": "https://<r2-signed-url>",
      "imageAltText": "Dr. Sok in a white coat",
      "serviceSlugs": ["colonoscopy", "gastroscopy"]
    }
  ]
}
```

- Every field is present; optional values are `null`, never omitted. `tags` is `[]`, never `null`.
- `GET /public/doctors/{slug}` returns the same item plus `services` (PC).
- With `?lang=km` every translated field (`title`, `bio`, `education`, `tags`,
  `imageAltText`) is the Khmer value. No fallback language (I18N-01, I18N-02).

### `PUT /api/v1/admin/doctors/{id}/profile`

```json
{
  "slug": "dr-sok",
  "practiceStartYear": 2010,
  "publicationsCount": 12,
  "translations": [
    { "languageCode": "km", "title": "…", "bio": "…", "education": "…", "tags": ["…"], "imageAltText": null },
    { "languageCode": "en", "title": "Gastroenterologist", "bio": "…", "education": "MD, University of Health Sciences, Phnom Penh", "tags": ["Endoscopy", "Digestive health"], "imageAltText": null },
    { "languageCode": "zh", "title": "…", "bio": null, "education": null, "tags": [], "imageAltText": null }
  ]
}
```

- PC-05 rules for `slug`, `title` and `bio` are unchanged.
- `practiceStartYear` and `publicationsCount` are required keys whose value may be `null`.
- `education` and `tags` are required keys in each translation; `imageAltText` is optional (DP-08).
- Response `200` with the profile, `imageUrl`, `serviceIds` and a new `ETag`.

### `PUT /api/v1/admin/doctors/{id}/image`

`multipart/form-data` with the same parts as `PUT /admin/services/{id}/image`: `image` (JPEG,
PNG or WebP, up to 5 MiB and 40 million pixels) and `altTexts` (`application/json`, exactly
three `{ "languageCode", "imageAltText" }`, one per `km`, `en`, `zh`). The doctor switches to
the new photo only after the R2 upload and the PostgreSQL update both succeed. Returns `200`
with the admin profile body and the new `ETag`.

### `DELETE /api/v1/admin/doctors/{id}/image`

No body. Returns `200` with the admin profile body (`imageUrl: null`, every `imageAltText: null`)
and the new `ETag`. Deleting when there is no photo changes nothing.

### Errors

As for PC, plus:

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `practiceStartYear` or `publicationsCount` out of range; `education` too long; too many, empty, long or duplicate tags; missing `image` or `altTexts`; alt text missing, repeated or over 200 characters; `imageAltText` sent for a doctor without a photo; undecodable image |
| 409 | `TRANSLATION_MISSING` | Photo upload for a doctor without a profile in all three languages (DP-07) |
| 413 | `IMAGE_TOO_LARGE` | Image larger than 5 MiB |
| 415 | `UNSUPPORTED_IMAGE` | Image is not JPEG, PNG or WebP |
| 503 | `STORAGE_UNAVAILABLE` | R2 is unavailable or not configured |

### Migration

- `doctor` adds `practice_start_year smallint NULL`, `publications_count integer NULL`
  (`CHECK` 0–10000) and `image_media_id uuid NULL REFERENCES clinic.cms_media (id)`.
- `doctor_translation` (PC) adds `education varchar(500) NULL`,
  `tags text[] NOT NULL DEFAULT '{}'` and `image_alt_text varchar(200) NULL`.
- If the PC migration has not shipped, these columns go into it; otherwise into the next one.
  Existing doctors get `null` and `[]`, and have no photo.

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| `GET /public/doctors?lang=zh` | `200`; each item has every card field, translated values in Chinese |
| `practiceStartYear: 2010` in 2026 | `experienceYears: 16`; in 2027 it is `17` without an edit |
| `practiceStartYear: null` | `experienceYears: null` |
| Save six tags in `en`, or two equal tags | `400 VALIDATION_ERROR` |
| Save `"tags": []` in `km` | `200`; Khmer card shows no tags |
| Upload a WebP with three alt texts | `200`; `imageUrl` set in admin and public responses |
| Upload a photo for a doctor with no `zh` profile | `409 TRANSLATION_MISSING`; nothing stored |
| Upload a 6 MiB file or a GIF | `413` or `415`; nothing changes |
| Change only the `km` alt text through the profile `PUT` | `200`; photo and other alt texts unchanged |
| Remove the photo | `200`; `imageUrl` and every `imageAltText` are `null`; website shows the fallback picture |
| Two admins upload from the same `ETag` | First `200`; second `412`; second upload discarded |

### Open questions for the Team Lead

1. The current card has a "Book with this doctor" button. Patients do not choose a doctor
   (BK-02), so this revision expects the button to open the normal booking form with no doctor
   preselected, or to be relabelled. Confirm which.
2. Should `fullName` get a Khmer and Chinese spelling? This revision keeps one value (PC-05).


## Packages and package booking - revision, November 19, 2026

Requested by the user to complete the clinic's catalogue rules. Prepared for coordinated
PR review in both repositories. Builds on the public catalogue revision (PC-01: services
carry no public price; prices belong to packages). Roles, appointment and visit
transitions, and receptionist approval are unchanged. No doctor login is introduced.

### Business rules

- A package shows its price, included services, exclusions and preparation.
- One booking request names **one service or one package** and creates **one** `PENDING`
  appointment for receptionist review.
- A package visit is one appointment. Its length is the package's own `durationMinutes`
  plus `bufferMinutes`, set by the clinic, not the sum of the included services.
- The receptionist assigns one doctor on confirm, as for services (BK-02).

### Decisions

| ID | Decision | Rule |
| --- | --- | --- |
| PK-01 | Package data | New tables `package`, `package_translation` and `package_service`. Packages are created and edited by `ADMIN` through the routes below; there is no seed. A new package starts `INACTIVE`. |
| PK-02 | Price | `priceUsd` is required, US dollars, two decimals, `≥ 0`. `originalPriceUsd` is `null` or greater than `priceUsd`, shown crossed out. Informational only; booking never charges the patient (SV-06). |
| PK-03 | Duration and buffer | `durationMinutes` 5–480 and `bufferMinutes` 0–60, as for services (BK-05). The appointment copies both when created; later package edits do not change existing appointments (BK-07). |
| PK-04 | Included services | 1–30 distinct services in display order, stored in `package_service (package_id, service_id, sort_order)`, both `ON DELETE RESTRICT`. Inclusion is descriptive: it does not create extra appointments, booking windows or capacity use. Public responses list only `ACTIVE` included services. |
| PK-05 | Text | Per language: `name` 1–120, `shortDescription` 1–300, `description` 1–5000, `preparation` `null` or 1–3000, `exclusions` 0–20 items of 1–200 characters each. Plain text; blank lines separate paragraphs. |
| PK-06 | Public visibility | `ACTIVE` packages only. An active package has all three languages and at least one active included service (I18N-01, I18N-02: no fallback). |
| PK-07 | Booking selector | `POST /public/appointments` takes exactly one of `serviceId` or `packageId`. Package requests use `preferredPeriod`; `preferredStartTime` with `packageId` is `400 VALIDATION_ERROR` until package booking windows exist. |
| PK-08 | Capacity | The BK capacity rule applies unchanged, using the package's `durationMinutes + bufferMinutes`. |
| PK-09 | Confirm | Same body and rules as a period service request: reception picks doctor, date and time, may override `durationMinutes` (BK-06), and the whole visit plus buffer must fit one period. No doctor booking window is required. Doctor overlap still returns `409 DOCTOR_UNAVAILABLE`. |
| PK-10 | Appointment fields | Appointments store either `service_id` or `package_id` (a check constraint enforces exactly one). Admin list and detail add `bookingType` (`SERVICE` or `PACKAGE`), `packageId` and `packageName`. For package appointments `serviceId` and `serviceName` are `null`; for service appointments the package fields are `null`. |
| PK-11 | Images | Package images follow the service image rules and are phase 2. Until then `imageUrl` and `imageAltText` are `null`. |
| PK-12 | Order | Packages: `sortOrder`, then `slug`. Included services: `package_service.sort_order`. |

### Routes

| Method | Route | Access |
| --- | --- | --- |
| GET | /api/v1/public/packages?lang= | Public (new) |
| GET | /api/v1/public/packages/{slug}?lang= | Public (new) |
| GET | /api/v1/public/booking-periods?date=&serviceId= or &packageId= | Public (changed: accepts `packageId`) |
| POST | /api/v1/public/appointments | Public (changed: accepts `packageId`) |
| GET | /api/v1/admin/packages | ADMIN (new) |
| POST | /api/v1/admin/packages | ADMIN (new) |
| GET | /api/v1/admin/packages/{id} | ADMIN (new) |
| PUT | /api/v1/admin/packages/{id} | ADMIN, `If-Match` required (new) |
| PATCH | /api/v1/admin/packages/{id}/activate | ADMIN, `If-Match` required (new) |
| PATCH | /api/v1/admin/packages/{id}/deactivate | ADMIN, `If-Match` required (new) |

Public routes require `lang`, return `Content-Language` and `Cache-Control: no-store`,
like the service list.

### `GET /api/v1/public/packages?lang=en`

```json
{
  "data": [
    {
      "id": "8b2e4f1a-6c3d-4e5f-9a7b-1c2d3e4f5a60",
      "slug": "annual-health-check",
      "name": "Annual Health Check",
      "shortDescription": "Blood tests, chest X-ray and a doctor's review.",
      "priceUsd": 120.00,
      "originalPriceUsd": 150.00,
      "durationMinutes": 120,
      "imageUrl": null,
      "imageAltText": null,
      "includedServiceSlugs": ["blood-test", "chest-x-ray", "general-consultation"]
    }
  ]
}
```

`data` is `[]` when there are no active packages. `id` is sent as `packageId` when booking.

### `GET /api/v1/public/packages/{slug}?lang=en`

```json
{
  "data": {
    "id": "8b2e4f1a-6c3d-4e5f-9a7b-1c2d3e4f5a60",
    "slug": "annual-health-check",
    "name": "Annual Health Check",
    "shortDescription": "Blood tests, chest X-ray and a doctor's review.",
    "description": "A yearly check of your general health.",
    "preparation": "Do not eat for 8 hours before the visit. Bring previous results.",
    "priceUsd": 120.00,
    "originalPriceUsd": 150.00,
    "durationMinutes": 120,
    "imageUrl": null,
    "imageAltText": null,
    "includedServices": [
      { "slug": "blood-test", "name": "Blood Test", "shortDescription": "Full blood count." },
      { "slug": "chest-x-ray", "name": "Chest X-ray", "shortDescription": "Image of heart and lungs." }
    ],
    "exclusions": ["Specialist follow-up", "Medication"]
  }
}
```

- Every field is present; optional values are `null`, never omitted. `exclusions` is `[]` when empty.
- No `bufferMinutes`, `status` or `sortOrder` in public responses.

### `GET /api/v1/public/booking-periods?date=2026-11-20&packageId=…`

Unchanged response. Exactly one of `serviceId` or `packageId` is required; both or neither
is `400 VALIDATION_ERROR`. An unknown or inactive package is `404 PACKAGE_NOT_FOUND`.
`GET /public/booking-slots` stays service-only.

### `POST /api/v1/public/appointments` with a package

```json
{
  "packageId": "8b2e4f1a-6c3d-4e5f-9a7b-1c2d3e4f5a60",
  "preferredDate": "2026-11-25",
  "preferredPeriod": "MORNING",
  "language": "en",
  "patient": { "fullName": "Sok Dara", "phone": "012 345 678", "email": null },
  "notes": null
}
```

- Exactly one of `serviceId` or `packageId`; otherwise `400 VALIDATION_ERROR`.
- With `packageId`, `preferredPeriod` is required and `preferredStartTime` is not allowed.
- `201` with the same `BookingConfirmation` as today, `status: PENDING`.
- Idempotency, rate limit, booking window and `409 PERIOD_FULL` rules are unchanged; the
  idempotency body includes `packageId`.

### Admin package body

`POST /admin/packages` and `PUT /admin/packages/{id}`:

```json
{
  "slug": "annual-health-check",
  "priceUsd": 120.00,
  "originalPriceUsd": 150.00,
  "durationMinutes": 120,
  "bufferMinutes": 15,
  "sortOrder": 10,
  "includedServiceIds": ["3f1c…", "7a9d…"],
  "translations": [
    {
      "languageCode": "en",
      "name": "Annual Health Check",
      "shortDescription": "Blood tests, chest X-ray and a doctor's review.",
      "description": "A yearly check of your general health.",
      "preparation": "Do not eat for 8 hours before the visit.",
      "exclusions": ["Specialist follow-up", "Medication"]
    }
  ]
}
```

- `slug`: lowercase letters, digits and hyphens, 1–80 characters, unique among packages.
- `includedServiceIds`: 1–30 distinct existing services (`ACTIVE` or `INACTIVE`), in display order.
- `translations`: each `languageCode` at most once. On `PUT`, languages not sent keep their
  saved text; an `ACTIVE` package must still have all three, otherwise `409 TRANSLATION_MISSING`.
- `POST` returns `201` with `Location`, the package and `ETag: "0"`; the package is `INACTIVE`.
- `PUT` replaces every other field and returns `200` with a new `ETag`.
- Admin responses add `id`, `status`, `version`, `updatedAt`, `missingLanguages` and every saved
  translation, like the admin service (SA-02).
- `activate` requires all three languages and at least one `ACTIVE` included service, otherwise
  `409 TRANSLATION_MISSING` or `409 PACKAGE_SERVICES_INACTIVE`. `deactivate` hides the package
  publicly; existing appointments are unchanged.

### Errors

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Bad `lang`, slug, price, duration, buffer or text; duplicate language; bad `includedServiceIds`; both or neither of `serviceId` / `packageId`; `preferredStartTime` with `packageId` |
| 404 | `PACKAGE_NOT_FOUND` | Unknown or inactive package slug or `packageId` (public); unknown package id (admin) |
| 404 | `SERVICE_NOT_FOUND` | An `includedServiceIds` entry does not exist |
| 409 | `PACKAGE_SLUG_TAKEN` | Another package has the slug |
| 409 | `TRANSLATION_MISSING` | Activating, or saving an active package, without all three languages |
| 409 | `PACKAGE_SERVICES_INACTIVE` | Activating a package with no active included service |
| 409 | `PERIOD_FULL` / `DOCTOR_UNAVAILABLE` | As for service bookings and confirmation |
| 412 / 428 | `VERSION_MISMATCH` / `VERSION_REQUIRED` | As for other admin edits |

### Migration

- Flyway adds `package`, `package_translation` (with `exclusions` as `text[]`) and `package_service`.
- `appointment.service_id` becomes nullable; add `appointment.package_id` (FK, `ON DELETE RESTRICT`)
  and `CHECK (num_nonnulls(service_id, package_id) = 1)`. Existing appointments keep `service_id`.
- No packages are seeded; the clinic enters real packages and prices.

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| `GET /public/packages?lang=en` with one active and one inactive package | `200`; one package with `priceUsd` |
| `GET /public/packages/annual-health-check?lang=km` | `200`; Khmer text, included active services, exclusions |
| Package detail where one included service is inactive | That service is not listed |
| Inactive package slug | `404 PACKAGE_NOT_FOUND` |
| Book a package with `preferredPeriod: MORNING` and capacity left | `201`, `PENDING`; appointment copies package duration and buffer |
| Book with both `serviceId` and `packageId` | `400 VALIDATION_ERROR` |
| Book a package with `preferredStartTime` | `400 VALIDATION_ERROR` |
| 120 + 15 minute package in a morning with 400 of 480 minutes used | `409 PERIOD_FULL` |
| Confirm a package appointment with a doctor who is free | `200 CONFIRMED`; no booking window needed |
| Confirm two overlapping package visits for one doctor at once | One `200`, one `409 DOCTOR_UNAVAILABLE` |
| Admin list shows a package appointment | `bookingType: PACKAGE`, `packageName` set, `serviceName: null` |
| Activate a package missing `zh` | `409 TRANSLATION_MISSING` |
| Two packages saved with the same slug | Second gets `409 PACKAGE_SLUG_TAKEN` |
| Edit a package's duration after a booking | Existing appointment keeps its copied duration |

### Open questions for the Team Lead

1. Can a package visit need more than one doctor or staff member? This revision assigns one doctor.
2. Should packages support exact-time requests? This revision allows periods only (PK-07).
3. A package longer than a period (for example 300 minutes in a 240-minute morning) can be
   requested but never confirmed. Should the admin editor block it? This revision does not.


## Preferred doctor - revision, November 20, 2026

> **Status:** Approved by the Team Lead on November 20, 2026, including PD-01 to PD-10: patients
> may name an optional, non-binding preferred doctor. Implemented with the PRs that add this
> section in both repositories. **Changes AP-06** and the PC business rule "patients still never choose a
> doctor". BK-02 is unchanged: the receptionist assigns the doctor on confirm. Roles, appointment
> and visit transitions, and receptionist approval are unchanged. No doctor login is introduced.

### Why

Patients see public doctor profiles linked to each service (PC-04, PC-06) and want to ask for
one of them. Until now the form could only put a preferred doctor in the notes, which reception
could miss. A stored, optional preference lets reception see it next to the request, while the
receptionist still decides who treats the patient.

### Decisions

| ID | Decision | Rule |
| --- | --- | --- |
| PD-01 | Request field | `POST /public/appointments` accepts optional `preferredDoctorSlug` (string or `null`). Absent or `null` means no preference. The public API never exposes doctor ids (PC), so the doctor is named by `slug`. |
| PD-02 | Which doctors | Service requests only. The doctor must be public (PC-04) and linked to the requested service (PC-06), as listed in `GET /public/services/{slug}` `doctors`. An unknown or non-public slug returns `404 DOCTOR_NOT_FOUND`; a public doctor not linked to the service, or any preference with `packageId`, returns `400 VALIDATION_ERROR` with `errors[].field = preferredDoctorSlug`. Nothing is created on failure. |
| PD-03 | Storage | The appointment stores the doctor's id in `preferred_doctor_id`, so a later slug change does not lose it. Like the preferred date (BK-03) it is never overwritten, including on confirm or reject. |
| PD-04 | Not binding | The preference reserves nothing and is not an availability check. `GET /public/booking-periods` and `GET /public/booking-slots` are unchanged and do not filter by doctor. Capacity rules (BK, PK-08) are unchanged. |
| PD-05 | Exact-time requests | When the exact-time revision allocates a doctor interval at submit, it picks the preferred doctor if that doctor has an eligible, free window for the requested start; otherwise it allocates any eligible doctor as before. The preference never causes `409 SLOT_UNAVAILABLE` on its own. |
| PD-06 | Confirm | Body and rules unchanged (BK-02, PK-09). Reception may assign the preferred doctor or any other active doctor. No reason is required when assigning someone else. |
| PD-07 | Reception view | Admin list items add `preferredDoctorName` (the doctor's current `fullName`); detail adds `preferredDoctorId`. Both are `null` when there is no preference. The doctor stays visible here even if later made inactive or non-public. |
| PD-08 | Public response | `BookingConfirmation` is unchanged and does not echo the preference. |
| PD-09 | Idempotency | The idempotency body includes `preferredDoctorSlug`; the same key with a different preference returns `409 IDEMPOTENCY_CONFLICT` (AP-03). |
| PD-10 | Website | The booking form offers "No preference" (the default) and the public doctors linked to the chosen service; it hides the choice for packages. The doctor card's "Book with this doctor" button opens the form with that doctor preselected as the preference. This answers open question 1 of the doctor profile revision. |

### Routes

| Method | Route | Change |
| --- | --- | --- |
| POST | /api/v1/public/appointments | Accepts `preferredDoctorSlug` |
| GET | /api/v1/admin/appointments | Items add `preferredDoctorName` |
| GET | /api/v1/admin/appointments/{id} | Adds `preferredDoctorId` and `preferredDoctorName` |

Status-change routes return the detail body, so they carry the new fields too.

### `POST /api/v1/public/appointments` with a preference

```json
{
  "serviceId": "3f1c2a4e-8b7d-4c1a-9e2f-5d6b7a8c9d01",
  "preferredDate": "2026-11-25",
  "preferredPeriod": "MORNING",
  "preferredDoctorSlug": "dr-sok",
  "language": "en",
  "patient": { "fullName": "Sok Dara", "phone": "012 345 678", "email": null },
  "notes": null
}
```

`201` with the same `BookingConfirmation` as today, `status: PENDING`.

### `GET /api/v1/admin/appointments` item

```json
{
  "id": "b2d4…",
  "reference": "SK-20261125-0003",
  "status": "PENDING",
  "serviceName": "Colonoscopy",
  "preferredDate": "2026-11-25",
  "preferredPeriod": "MORNING",
  "preferredDoctorName": "Dr. Sok",
  "doctorName": null,
  "startAt": null
}
```

Other list fields are as before; only `preferredDoctorName` is new.

### Errors

No new codes.

| Status | Type | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | `preferredDoctorSlug` is not a valid slug, names a doctor not linked to the service, or is sent with `packageId` |
| 404 | `DOCTOR_NOT_FOUND` | `preferredDoctorSlug` is unknown or the doctor is not public |
| 409 | `IDEMPOTENCY_CONFLICT` | Same `Idempotency-Key`, different `preferredDoctorSlug` |

### Migration

- Flyway adds `appointment.preferred_doctor_id uuid NULL REFERENCES clinic.doctor (id) ON DELETE RESTRICT`
  and `CHECK (preferred_doctor_id IS NULL OR service_id IS NOT NULL)`.
- Existing appointments get `null`. Preferences written into old notes stay in the notes.

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| Book a service with no `preferredDoctorSlug` | `201`, `PENDING`; admin shows `preferredDoctorName: null` |
| Book with the slug of a public doctor linked to the service | `201`, `PENDING`; admin list shows `preferredDoctorName` |
| Slug of a public doctor not linked to the service | `400 VALIDATION_ERROR`, field `preferredDoctorSlug`; nothing created |
| Unknown slug, or an inactive doctor's slug | `404 DOCTOR_NOT_FOUND`; nothing created |
| Package request with `preferredDoctorSlug` | `400 VALIDATION_ERROR` |
| Confirm with a different doctor than preferred | `200 CONFIRMED`; `doctorId` is the assigned doctor, `preferredDoctorId` unchanged |
| Exact request; preferred doctor free at that start | `201`; the reservation holds the preferred doctor |
| Exact request; preferred doctor busy, another eligible doctor free | `201`; another doctor is reserved |
| Same `Idempotency-Key`, different preference | `409 IDEMPOTENCY_CONFLICT` |
| Preferred doctor made inactive after booking | Request unchanged; admin still shows the preference |

### Open questions for the Team Lead

1. Should the patient's preference be shown back on the booking confirmation screen? This
   revision keeps it out of the API response (PD-08); the form can show what was selected.
2. Should reception see a hint when the preferred doctor has an overlap at the requested time?
   This revision adds none; confirm still returns `409 DOCTOR_UNAVAILABLE` on an actual overlap.


## Patient Telegram updates - revision, November 20, 2026

> **Status:** Proposed. Awaits Team Lead approval through PR review in both repositories.
> **Changes AP-14** for patient notifications only: a patient may opt in, per booking, to
> Telegram messages about that booking. Cancellation, no-show, rescheduling and other AP-14
> items stay out of scope. Roles, appointment and visit transitions, and receptionist approval
> are unchanged. Staff still change statuses only in the authenticated dashboard; Telegram never
> changes an appointment. Builds on the staff notification outbox and the `telegram_subscription`
> storage foundation (V12, V13).

### Why

Patients wait for reception to confirm a request and today hear back only by phone. An opt-in
Telegram message when reception confirms or rejects, and a reminder the day before, tells them
without a call. Patients need no account and never type a chat ID or phone number.

### Patient flow

1. The patient books. The confirmation screen shows **Get updates on Telegram** with an
   **Open Telegram** button (no QR code). The button opens
   `https://t.me/<bot>?start=<link code>`.
2. Telegram opens the clinic bot and the patient taps **START**. Telegram sends the backend
   `/start <link code>` from the patient's private chat.
3. The backend links that private chat to the booking and replies **You're connected**.
4. When reception confirms or rejects the request in the dashboard, or the day before a confirmed
   visit, the bot sends the patient a message. `/stop` turns updates off.

### Decisions

| ID | Decision | Rule |
| --- | --- | --- |
| PT-01 | Opt-in only | Nothing is sent to a patient unless they tapped START with a valid link code for that booking. A chat ID or phone number is never accepted as proof of ownership. |
| PT-02 | Link code | `POST /public/appointments` returns `telegramLinkUrl`, or `null` when patient updates are disabled. The code is 43 URL-safe characters derived as HMAC-SHA256(`TELEGRAM_LINK_SECRET`, appointment id), so an idempotent replay (AP-03) returns the same URL. Only its SHA-256 hash is stored. Single use; expires 24 hours after the booking was created. |
| PT-03 | Webhook | `POST /api/v1/telegram/webhook` receives Telegram updates. It requires header `X-Telegram-Bot-Api-Secret-Token` equal to `TELEGRAM_WEBHOOK_SECRET`; otherwise `401`. It returns `200` for every authenticated update, including ones it ignores, so Telegram does not retry them. It is registered once with Telegram's `setWebhook` (deployment step). |
| PT-04 | Linking | `/start <code>` in a **private** chat links that chat to the booking when the code is valid, unused and unexpired and the booking is `PENDING` or `CONFIRMED`. The code is then marked used and a `telegram_subscription` row is saved with the consent time. Group chats are ignored. A booking has one recipient: a second chat gets "already connected"; the same chat again gets "You're connected" (idempotent). Invalid or expired codes get a short help reply. |
| PT-05 | Messages | `LINKED` (You're connected), `CONFIRMED`, `REJECTED` and `REMINDER`. `ARRIVED`, `IN_PROGRESS` and `COMPLETED` send nothing. Content follows the samples below, in one Telegram block quote, HTML-escaped. Never included: notes, email, the staff rejection reason. |
| PT-06 | Language | The booking's `language` (`km`, `en`, `zh`; AP-05). Wording is reviewed by the clinic before release. |
| PT-07 | Delivery | Patient messages use the existing outbox (`notification_job`) and its retries, queued in the same transaction as the status change. A failed or slow Telegram never blocks or rolls back reception's action. Each job is sent only if the subscription is still active when it is sent, including on retry. |
| PT-08 | Reminder | Once, at 17:00 Asia/Phnom_Penh on the day before, for `CONFIRMED` appointments starting the next clinic day with an active subscription. Not sent if confirmed after 17:00 for the next day. At most one reminder per appointment. |
| PT-09 | `/stop` | Revokes every active subscription of that chat (V13 keeps consent and revocation times). Queued jobs for those bookings are not sent. The bot replies once to confirm. |
| PT-10 | Reception view | Admin detail adds `patientTelegramConnected` (boolean). Reception does nothing extra; messages go automatically on Confirm or Reject. |
| PT-11 | One bot | The staff bot also serves patients. Staff summaries still go only to `TELEGRAM_CHAT_ID`. |
| PT-12 | Off switch | `TELEGRAM_PATIENT_UPDATES_ENABLED` (default `false`). When off, `telegramLinkUrl` is `null`, the webhook returns `404`, and no patient jobs are queued. Staff summaries are unaffected. |

### Routes

| Method | Route | Access | Change |
| --- | --- | --- | --- |
| POST | /api/v1/public/appointments | Public | Response adds `telegramLinkUrl` |
| POST | /api/v1/telegram/webhook | Telegram, secret header | New |
| GET | /api/v1/admin/appointments/{id} | ADMIN, RECEPTION | Adds `patientTelegramConnected` |

### `POST /api/v1/public/appointments` response

```json
{
  "data": {
    "id": "b2d4…",
    "reference": "SK-20261120-0008",
    "status": "PENDING",
    "preferredDate": "2026-11-20",
    "preferredPeriod": "AFTERNOON",
    "preferredStartTime": "16:00",
    "telegramLinkUrl": "https://t.me/Sokhasaclinic_bot?start=Qm9va2luZ0xpbmtDb2RlRXhhbXBsZTEyMzQ1Njc4OTA"
  }
}
```

The website shows the Telegram card only when `telegramLinkUrl` is not `null`, and opens it in
a new tab. It is shown once, on the confirmation screen; it is not emailed or stored by the
website.

### `POST /api/v1/telegram/webhook`

Body: a Telegram `Update`. Handled: `message.text` of `/start <code>`, `/start` and `/stop` in
chats whose `type` is `private`. Everything else is acknowledged with `200` and ignored. The
reply to the patient is sent through the outbox, not in the webhook response.

### Message samples (English)

**LINKED**
```
You're connected
We'll send updates for your booking here.
Reference: SK-20261120-0008
Service: High-Resolution Digital Ultrasound & Organ Imaging
Requested: Fri, 20 Nov 2026, 16:00
Status: Waiting for reception to confirm
Send /stop any time to turn off updates.
```

**CONFIRMED**
```
Your appointment is confirmed
Reference: SK-20261120-0008
Service: High-Resolution Digital Ultrasound & Organ Imaging
Date: Fri, 20 Nov 2026
Time: 16:00 – 16:30
Doctor: Dr. Sok
Clinic: <clinic name and address>
Phone: <clinic phone>
Please arrive 10 minutes early.
```

**REJECTED**: "We couldn't confirm your request", the reference and requested time, and how to
book again or call. **REMINDER**: "Your appointment is tomorrow", with date, time, service,
doctor and reference. The clinic name, address and phone come from configuration.

### Errors

| Status | Type | When |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | Webhook secret header missing or wrong |
| 404 | `NOT_FOUND` | Webhook called while patient updates are disabled |

Invalid, used or expired link codes are not HTTP errors: the webhook returns `200` and the bot
replies with help text.

### Configuration

| Variable | Purpose |
| --- | --- |
| `TELEGRAM_PATIENT_UPDATES_ENABLED` | PT-12 switch, default `false` |
| `TELEGRAM_BOT_USERNAME` | Builds `telegramLinkUrl`, for example `Sokhasaclinic_bot` |
| `TELEGRAM_WEBHOOK_SECRET` | PT-03 header value, 32+ random characters |
| `TELEGRAM_LINK_SECRET` | PT-02 HMAC key, 32+ random characters |
| `CLINIC_DISPLAY_NAME`, `CLINIC_ADDRESS`, `CLINIC_PHONE` | Shown in patient messages |

Enabling patient updates with any of these missing fails startup, like the staff settings.

### Migration

- `appointment_telegram_link (appointment_id PK FK, code_hash UNIQUE, expires_at, used_at)`.
- `notification_job` gains `recipient` (`STAFF` or `PATIENT`) and the patient event types;
  existing rows become `STAFF`. Patient jobs reference their subscription so delivery can check
  that it is still active.

### Acceptance cases

| Scenario | Expected result |
| --- | --- |
| Patient updates disabled | `telegramLinkUrl: null`; no Telegram card; webhook `404` |
| Book, open the link, tap START in a private chat | Subscription saved; LINKED message within seconds |
| Same link opened again from the same chat | "You're connected" again; one subscription |
| Same link from a second Telegram account | "Already connected"; no second recipient |
| Link used after 24 hours | Help reply; no subscription |
| `/start <code>` sent in a group | Ignored |
| Webhook without the secret header | `401`; nothing changes |
| Reception confirms a linked booking | CONFIRMED message with doctor and time |
| Reception rejects with reason "Doctor on leave" | REJECTED message without the reason |
| Reception confirms a booking that never linked | No patient job |
| Patient sends `/stop`, then reception confirms | No CONFIRMED message |
| Telegram is down when reception confirms | Confirm returns `200`; message retried later |
| Confirmed for tomorrow at 10:00 today | REMINDER at 17:00 today |
| Confirmed at 18:00 for tomorrow | No reminder |
| Booking language `km` | Khmer messages |

### Open questions for the Team Lead

1. Real clinic name, address and phone for the messages.
2. Reminder time: 17:00 the day before, or another time?
3. Khmer and Chinese wording: who reviews the translations before release?
