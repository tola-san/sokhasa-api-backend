-- Internal storage foundation only. Public bot linking and patient delivery are not enabled.
CREATE TABLE clinic.telegram_subscription (
    id uuid PRIMARY KEY,
    appointment_id uuid NOT NULL UNIQUE REFERENCES clinic.appointment(id) ON DELETE CASCADE,
    chat_id bigint NOT NULL CHECK (chat_id > 0),
    consented_at timestamptz NOT NULL,
    revoked_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT telegram_subscription_revocation_check CHECK (
        revoked_at IS NULL OR revoked_at >= consented_at
    )
);

-- One private chat may opt in to several bookings; an eventual /stop can find all active ones.
CREATE INDEX telegram_subscription_active_chat_idx ON clinic.telegram_subscription(chat_id)
    WHERE revoked_at IS NULL;
