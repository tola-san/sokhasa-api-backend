-- Fields and tables for the appointment API approved on November 18, 2026 (decisions AP-01 to AP-16).

-- AP-05: what the patient sends with a request. AP-01: a human-readable reference per request.
ALTER TABLE clinic.appointment
    ADD COLUMN reference     varchar(20),
    ADD COLUMN language_code varchar(2),
    ADD COLUMN patient_email varchar(254),
    ADD COLUMN notes         varchar(500);

-- Requests made before this migration get a reference in creation order and English messages.
UPDATE clinic.appointment a
SET reference = numbered.reference,
    language_code = 'en'
FROM (
    SELECT id,
           'SK-' || to_char(preferred_date, 'YYYYMMDD') || '-' ||
           lpad(row_number() OVER (PARTITION BY preferred_date ORDER BY created_at, id)::text, 4, '0') AS reference
    FROM clinic.appointment
) numbered
WHERE a.id = numbered.id;

ALTER TABLE clinic.appointment
    ALTER COLUMN reference SET NOT NULL,
    ALTER COLUMN language_code SET NOT NULL,
    ADD CONSTRAINT appointment_reference_key UNIQUE (reference),
    ADD CONSTRAINT appointment_language_check CHECK (language_code IN ('km', 'en', 'zh')),
    ADD CONSTRAINT appointment_notes_check CHECK (notes IS NULL OR btrim(notes) <> '');

-- AP-01: the last reference number used for each preferred date. Allocated with an upsert, so two
-- requests for the same date never get the same number.
CREATE TABLE clinic.appointment_reference_counter (
    day        date PRIMARY KEY,
    last_value int  NOT NULL CHECK (last_value > 0)
);

INSERT INTO clinic.appointment_reference_counter (day, last_value)
SELECT preferred_date, count(*) FROM clinic.appointment GROUP BY preferred_date;

-- AP-03: a repeated booking submit with the same Idempotency-Key returns the first result. Rows
-- older than 24 hours are deleted by IdempotencyKeyCleanup.
CREATE TABLE clinic.booking_idempotency_key (
    idempotency_key uuid        PRIMARY KEY,
    request_hash    varchar(64) NOT NULL,
    appointment_id  uuid        NOT NULL REFERENCES clinic.appointment (id) ON DELETE CASCADE,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX booking_idempotency_key_created_idx ON clinic.booking_idempotency_key (created_at);

-- AP-02: booking settings the clinic can change without a deploy. Exactly one row.
CREATE TABLE clinic.booking_setting (
    id          smallint    PRIMARY KEY CHECK (id = 1),
    window_days int         NOT NULL CHECK (window_days BETWEEN 1 AND 365),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

INSERT INTO clinic.booking_setting (id, window_days) VALUES (1, 60);

-- The staff member's name at the time of the change, shown in the history (Keycloak name).
ALTER TABLE clinic.appointment_status_history
    ADD COLUMN actor_name varchar(255);
