-- Patient Telegram updates (PT-01 to PT-12, approved November 20, 2026).

-- PT-02: one single-use link code per booking. Only the code's SHA-256 hash is stored.
CREATE TABLE clinic.appointment_telegram_link (
    appointment_id uuid PRIMARY KEY REFERENCES clinic.appointment(id) ON DELETE CASCADE,
    code_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz
);

-- PT-07: patient messages share the staff outbox and its retries.
ALTER TABLE clinic.notification_job
    -- Help replies to a chat that is not linked to any booking have no appointment.
    ALTER COLUMN appointment_id DROP NOT NULL,
    ADD COLUMN recipient varchar(10) NOT NULL DEFAULT 'STAFF'
        CHECK (recipient IN ('STAFF', 'PATIENT')),
    -- Delivery re-checks that this subscription is still active, including on retry.
    ADD COLUMN subscription_id uuid REFERENCES clinic.telegram_subscription(id) ON DELETE CASCADE,
    DROP CONSTRAINT notification_job_event_type_check,
    ADD CONSTRAINT notification_job_event_type_check CHECK (event_type IN (
        'APPOINTMENT_CREATED', 'PATIENT_LINKED', 'PATIENT_CONFIRMED', 'PATIENT_REJECTED',
        'PATIENT_REMINDER', 'PATIENT_REPLY')),
    DROP CONSTRAINT notification_job_status_check,
    ADD CONSTRAINT notification_job_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELLED')),
    ADD CONSTRAINT notification_job_patient_check CHECK (
        (recipient = 'STAFF' AND event_type = 'APPOINTMENT_CREATED' AND appointment_id IS NOT NULL)
        OR (recipient = 'PATIENT' AND event_type <> 'APPOINTMENT_CREATED'));
