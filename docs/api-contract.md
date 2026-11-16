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

## CMS (approved November 16, 2026)

The CMS page and upload contract is approved as specified in
[API documentation sections 4, 5.1 and 13.2](API-DOCUMENTATION.md).
All admin routes require the Keycloak `ADMIN` role.

| Method | Route |
| --- | --- |
| GET | /api/v1/admin/pages |
| POST | /api/v1/admin/pages |
| GET | /api/v1/admin/pages/{id} |
| PUT | /api/v1/admin/pages/{id} |
| PATCH | /api/v1/admin/pages/{id}/publish |
| PATCH | /api/v1/admin/pages/{id}/unpublish |
| DELETE | /api/v1/admin/pages/{id} |
| POST | /api/v1/admin/media/upload |
| GET | /api/v1/public/pages/{slug}?lang=en |

Updating, publishing, unpublishing, or deleting a page requires its latest strong `ETag`
in `If-Match`. Publishing replaces the full live language set. Public reads return
`404` when the requested live translation is unavailable.
Uploads accept JPEG, PNG and WebP up to 5 MiB. Decoding is limited to 40 million pixels
to bound memory usage. Image download URLs expire after five minutes.
