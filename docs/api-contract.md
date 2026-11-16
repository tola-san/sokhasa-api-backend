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
