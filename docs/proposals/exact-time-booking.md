# Exact-time booking proposal

Status: Draft for Team Lead review, November 19, 2026. This document does not
change the approved API contract or authorize implementation.

Follow-up: the user requested backend implementation on November 19, 2026.
The resulting extension is documented in `docs/api-contract.md` for PR review;
this original proposal remains design background.

Requested outcome: update the public booking form to offer exact times using the
provided reference: an Indochina Time (GMT+7) label, availability heading for the
selected date, two columns of outlined time buttons, and a solid purple selected
button with white text.

## Approval needed

The [approved contract](../api-contract.md) explicitly requires period selection
(BK-01), reception assigning the exact time and doctor (BK-02), and period-level
availability (AP-11). Exact-time availability requires a source of doctor working
hours, breaks, service eligibility and absences, which the current contract does
not define. The following changes are proposals for review, not approved rules.

| Decision | Proposed behavior |
| --- | --- |
| Patient selection | Service, date, and exact requested start time on a 30-minute grid. The grid controls start times; service duration and buffer retain their existing values. |
| Time zone | Interpret all dates and times in `Asia/Phnom_Penh`; return timestamps with `+07:00`; display “Indochina Time (GMT+7)”. |
| Approval | Every submission remains `PENDING`; reception must confirm or reject it. Selecting a time does not confirm the visit. |
| Pending reservations | Atomically reserve one eligible doctor's entire requested interval, including buffer, on submission. Keep this internal reservation separate from the confirmed doctor and times. |
| Staff schedules | Clinic Admin maintains service eligibility, working windows, breaks and date exceptions. Until management APIs are separately approved, use clinic-verified seed data and migrations. Missing schedule data means no exact times available. |
| Reception changes | Reception may choose another doctor or time during confirmation, as currently allowed, but must validate the full replacement interval and replace the pending reservation atomically. Preserve the original requested time. |
| Existing period requests | Continue supporting and displaying them. Enable exact-time booking only after a cutover plan accounts for outstanding period requests. |

The reference's 2:00 pm–5:00 pm list illustrates the design; it is not a clinic
schedule. Current placeholder afternoon hours end at 17:00, so a 17:00 start
cannot fit a positive service duration. Clinic approval of actual opening hours
is required before offering that time. Do not hard-code the reference date or
automatically select 4:30 pm.

## Proposed HTTP changes

All existing authentication, patient validation, booking-window, rate-limit,
idempotency, response-envelope and problem-details conventions remain applicable.

### Availability

Add public `GET /api/v1/public/booking-slots?date=2026-11-20&serviceId=<uuid>`.
Both parameters are required. Example response, illustrating a service with
30-minute duration and no buffer:

```json
{
  "data": {
    "date": "2026-11-20",
    "timeZone": "Asia/Phnom_Penh",
    "slots": [
      {
        "startAt": "2026-11-20T14:00:00+07:00",
        "endAt": "2026-11-20T14:30:00+07:00"
      },
      {
        "startAt": "2026-11-20T14:30:00+07:00",
        "endAt": "2026-11-20T15:00:00+07:00"
      }
    ]
  }
}
```

Return available starts only, sorted and deduplicated across eligible doctors.
`endAt` excludes the buffer; availability includes it. Do not expose doctor IDs,
patient information or remaining capacity publicly. Use `Cache-Control: no-store`.
An in-window date with no availability returns an empty `slots` array. An invalid
date or one outside the booking window returns `400 VALIDATION_ERROR`; an unknown
service returns `404 SERVICE_NOT_FOUND`.

Generate candidates on clinic-local `:00` and `:30` boundaries. Offer a candidate
only if it is in the future, the complete duration plus buffer fits within an
eligible doctor's working window and one booking period, and both doctor overlap
rules and existing period capacity permit it. Availability is a hint; submission
must recheck all conditions in a transaction.

### Submission and responses

Extend `POST /api/v1/public/appointments` with optional `preferredStartTime`
(`HH:mm`, clinic-local, on the 30-minute grid). Keep `preferredDate` required.
For exact-time requests, omit `preferredPeriod`; the server derives and stores it
from the requested interval. Reject a body containing both selectors with
`400 VALIDATION_ERROR`. Legacy bodies containing only `preferredPeriod` retain
their approved behavior. Require exactly one selector.

Example exact-time body (IDs abbreviated for readability):

```json
{
  "serviceId": "3f1c…",
  "preferredDate": "2026-11-20",
  "preferredStartTime": "14:30",
  "language": "en",
  "patient": { "fullName": "Sok Dara", "phone": "012 345 678", "email": null },
  "notes": "First visit"
}
```

Extend public creation responses and admin list/detail responses with nullable
`preferredStartTime`; existing period-only requests return `null`. Public creation
continues returning `status: PENDING`, the reference, date and derived period.
Keep confirmed `startAt`, `endAt` and `doctorId` separate from immutable patient
preferences. The confirm request remains unchanged. Include the requested exact
time in the reception calendar and appointment detail view.

Add `409 SLOT_UNAVAILABLE` when a valid exact start can no longer be reserved;
create nothing. Retain `PERIOD_FULL` for aggregate period capacity exhaustion.
On either conflict, the frontend refreshes availability and requires a new valid
selection. Retried successful submissions with the same idempotency key and body
must return their original result without another reservation.

## Concurrency, migration and release

- Reserve the whole half-open interval `[startAt, endAt + buffer)` for a doctor,
  including `PENDING` exact-time requests. Enforce overlap protection in PostgreSQL
  for pending reservations and confirmed visits together; a read-before-write
  availability check alone is insufficient.
- Retain period-capacity accounting and locks for compatibility with legacy
  bookings. Use one documented lock order across submission, confirmation,
  rejection and schedule edits. Revalidate schedules inside the transaction;
  schedule edits must not silently invalidate existing reservations.
- Reject releases a pending reservation in the same transaction. Confirmation
  replaces it with the confirmed reservation atomically. Keep existing lifecycle
  rules; do not add expiration, cancellation or rescheduling statuses.
- Outstanding period-only pending requests have no doctor interval. Before enabling
  exact-time selection for an affected service/date, reception must resolve those
  requests or the Team Lead must approve a migration/allocation policy. Period
  capacity alone cannot guarantee those patients an exact doctor interval.
- After approval, update `docs/api-contract.md` and `docs/openapi.yaml`, then sync
  the backend copies. Deliver coordinated frontend and backend PRs. The frontend
  must not enable exact-time submission before the backend and schedule data are
  ready.

## Frontend behavior and acceptance checks

- Show the chosen date with full weekday and month, using clinic-local time;
  show the timezone above a purple divider, as in the reference.
- Render available starts in a responsive two-column grid with outlined buttons;
  use purple `#8759A1` and white text for selection. Provide accessible selection
  semantics, visible keyboard focus and touch targets at least 44 px high.
- Clear selection when service/date changes or a refresh removes the selected
  time. Ignore stale availability responses. Show distinct loading, empty and
  failed states; do not fabricate available times after an API failure.
- Verify date/time display stays in GMT+7 when the browser uses another timezone.
- Verify a service longer than 30 minutes, buffers, breaks, closing boundaries,
  absent doctors and past starts all affect availability correctly.
- Verify two simultaneous requests for the last doctor interval produce one
  `201 PENDING` and one conflict, while different eligible doctors can support
  the same start when period capacity allows.
- Verify concurrent confirmation, rejection and schedule changes cannot create
  overlapping reservations; idempotent retries do not consume capacity twice.
- Verify legacy period requests and the existing appointment lifecycle still work.

Team Lead decisions required before implementation: approve the proposed request
fields and response shapes, pending doctor reservations, schedule ownership and
seed-data approach, actual clinic hours, and the legacy-request cutover policy.
