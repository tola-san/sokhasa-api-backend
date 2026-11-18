-- Booking core: doctors, patients, booking periods, appointments and their status history.
-- Implements the approved "Booking periods and service durations" contract (November 17, 2026):
-- BK-01 to BK-08, the capacity rule and the confirm rules. Request fields still in draft
-- (reference, language, email, notes, preferred doctor) are added with the HTTP routes later.

-- Needed by the doctor overlap exclusion constraint (uuid equality inside a GiST index).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- timestamptz + interval is only STABLE (a day or month interval depends on the time zone), so it
-- cannot appear in an index expression. Adding whole minutes never depends on the time zone, so
-- this wrapper is genuinely IMMUTABLE and lets the exclusion constraint include the buffer.
CREATE FUNCTION clinic.plus_minutes(ts timestamptz, minutes int) RETURNS timestamptz
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    RETURN ts + minutes * interval '1 minute';

-- Doctors are clinic resources with no login. No seed rows: the clinic supplies the real list.
CREATE TABLE clinic.doctor (
    id         uuid         PRIMARY KEY,
    full_name  varchar(120) NOT NULL CHECK (btrim(full_name) <> ''),
    status     varchar(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    sort_order int          NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

-- One row per phone number (E.164). Appointments keep their own copy of the submitted name and
-- phone, so a later booking never rewrites an earlier one.
CREATE TABLE clinic.patient (
    id         uuid         PRIMARY KEY,
    full_name  varchar(120) NOT NULL CHECK (btrim(full_name) <> ''),
    phone      varchar(16)  NOT NULL UNIQUE CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

-- BK-04: periods are data, editable without a deploy. Times are local clinic time (BK-08).
CREATE TABLE clinic.booking_period (
    code             varchar(20) PRIMARY KEY CHECK (code ~ '^[A-Z_]+$'),
    start_time       time        NOT NULL,
    end_time         time        NOT NULL,
    capacity_minutes int         NOT NULL CHECK (capacity_minutes > 0),
    sort_order       int         NOT NULL DEFAULT 0,
    CONSTRAINT booking_period_times_check CHECK (end_time > start_time)
);

-- Placeholder hours and capacities until the clinic confirms them (open question 2).
INSERT INTO clinic.booking_period (code, start_time, end_time, capacity_minutes, sort_order) VALUES
    ('MORNING',   '08:00', '12:00', 480, 1),
    ('AFTERNOON', '13:30', '17:00', 420, 2);

CREATE TABLE clinic.appointment (
    id                uuid         PRIMARY KEY,
    patient_id        uuid         NOT NULL REFERENCES clinic.patient (id),
    service_id        uuid         NOT NULL REFERENCES clinic.service (id) ON DELETE RESTRICT,
    patient_name      varchar(120) NOT NULL,
    patient_phone     varchar(16)  NOT NULL,
    -- BK-03: the patient's request, never overwritten.
    preferred_date    date         NOT NULL,
    preferred_period  varchar(20)  NOT NULL REFERENCES clinic.booking_period (code),
    -- BK-07: copied from the service when the request is created. BK-06: confirm may change the duration.
    duration_minutes  int          NOT NULL CHECK (duration_minutes BETWEEN 5 AND 480),
    buffer_minutes    int          NOT NULL CHECK (buffer_minutes BETWEEN 0 AND 60),
    status            varchar(12)  NOT NULL CHECK (status IN
                          ('PENDING', 'CONFIRMED', 'REJECTED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED')),
    -- Set on confirm (BK-02). confirmed_date and confirmed_period are the local date and the period
    -- that contain start_at; the capacity rule counts a confirmed appointment against them.
    doctor_id         uuid         REFERENCES clinic.doctor (id),
    start_at          timestamptz,
    end_at            timestamptz,
    confirmed_date    date,
    confirmed_period  varchar(20)  REFERENCES clinic.booking_period (code),
    version           bigint       NOT NULL DEFAULT 0,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    -- A confirmed (or later) appointment always has its doctor and time; a pending or rejected one never does.
    CONSTRAINT appointment_schedule_check CHECK (
        CASE WHEN status IN ('PENDING', 'REJECTED')
            THEN doctor_id IS NULL AND start_at IS NULL AND end_at IS NULL
                 AND confirmed_date IS NULL AND confirmed_period IS NULL
            ELSE doctor_id IS NOT NULL AND start_at IS NOT NULL AND end_at > start_at
                 AND confirmed_date IS NOT NULL AND confirmed_period IS NOT NULL
        END
    ),
    -- Confirm rules: one doctor cannot have overlapping active appointments, buffer included.
    -- Two receptionists confirming at once cannot both succeed (409 DOCTOR_UNAVAILABLE).
    CONSTRAINT appointment_doctor_no_overlap EXCLUDE USING gist (
        doctor_id WITH =,
        tstzrange(start_at, clinic.plus_minutes(end_at, buffer_minutes)) WITH &&
    ) WHERE (status IN ('CONFIRMED', 'ARRIVED', 'IN_PROGRESS'))
);

CREATE INDEX appointment_patient_idx ON clinic.appointment (patient_id);
CREATE INDEX appointment_service_idx ON clinic.appointment (service_id);
CREATE INDEX appointment_status_created_idx ON clinic.appointment (status, created_at);
-- Capacity lookups: pending requests by preferred slot, confirmed ones by confirmed slot.
CREATE INDEX appointment_preferred_slot_idx ON clinic.appointment (preferred_date, preferred_period)
    WHERE status = 'PENDING';
CREATE INDEX appointment_confirmed_slot_idx ON clinic.appointment (confirmed_date, confirmed_period)
    WHERE confirmed_date IS NOT NULL;

-- One row per status change, written in the same transaction as the change. The identity id keeps
-- changes made within the same millisecond in order.
CREATE TABLE clinic.appointment_status_history (
    id             bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    appointment_id uuid         NOT NULL REFERENCES clinic.appointment (id) ON DELETE CASCADE,
    from_status    varchar(12),
    to_status      varchar(12)  NOT NULL,
    actor_type     varchar(10)  NOT NULL CHECK (actor_type IN ('GUEST', 'STAFF')),
    -- Keycloak subject of the staff member; NULL for a guest.
    actor_id       varchar(255),
    reason         varchar(500),
    changed_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT appointment_history_actor_check CHECK ((actor_type = 'GUEST') = (actor_id IS NULL))
);

CREATE INDEX appointment_history_appointment_idx
    ON clinic.appointment_status_history (appointment_id, id);
