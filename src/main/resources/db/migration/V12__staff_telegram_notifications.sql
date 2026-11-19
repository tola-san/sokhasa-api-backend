-- One durable staff summary for each newly created appointment. No patient subscriptions.
CREATE TABLE clinic.notification_job (
    id uuid PRIMARY KEY,
    appointment_id uuid NOT NULL REFERENCES clinic.appointment(id),
    event_type varchar(40) NOT NULL CHECK (event_type = 'APPOINTMENT_CREATED'),
    chat_id varchar(32) NOT NULL,
    message text NOT NULL,
    status varchar(12) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    attempts int NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,
    last_error_code varchar(40),
    created_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    UNIQUE (appointment_id, event_type, chat_id)
);
CREATE INDEX notification_job_pending_idx ON clinic.notification_job(next_attempt_at, created_at)
    WHERE status = 'PENDING';
CREATE INDEX notification_job_lease_idx ON clinic.notification_job(lease_until)
    WHERE status = 'PROCESSING';
