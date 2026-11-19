-- Exact-time requests keep patient preferences separate from confirmed appointments.
-- No invented opening hours: the clinic must supply verified date/service/doctor windows.
CREATE TABLE clinic.doctor_booking_window (
    id uuid PRIMARY KEY,
    doctor_id uuid NOT NULL REFERENCES clinic.doctor(id),
    service_id uuid NOT NULL REFERENCES clinic.service(id),
    day date NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    CHECK (end_time > start_time),
    UNIQUE (doctor_id, service_id, day, start_time, end_time)
);
CREATE INDEX doctor_booking_window_lookup ON clinic.doctor_booking_window(service_id, day);

ALTER TABLE clinic.appointment
    ADD COLUMN preferred_start_time time,
    ADD COLUMN booking_window_id uuid REFERENCES clinic.doctor_booking_window(id),
    ADD COLUMN reserved_doctor_id uuid REFERENCES clinic.doctor(id),
    ADD COLUMN reserved_start_at timestamptz,
    ADD COLUMN reserved_end_at timestamptz,
    ADD CONSTRAINT appointment_exact_preference_check CHECK (
        (preferred_start_time IS NULL) = (booking_window_id IS NULL)
        AND (preferred_start_time IS NULL OR
            (extract(minute FROM preferred_start_time) IN (0, 30)
             AND extract(second FROM preferred_start_time) = 0))
    ),
    ADD CONSTRAINT appointment_reservation_check CHECK (
        CASE WHEN status = 'PENDING' AND preferred_start_time IS NOT NULL
        THEN reserved_doctor_id IS NOT NULL AND reserved_start_at IS NOT NULL
             AND reserved_end_at IS NOT NULL AND reserved_end_at > reserved_start_at
        ELSE reserved_doctor_id IS NULL AND reserved_start_at IS NULL AND reserved_end_at IS NULL
        END
    );

-- A single exclusion constraint covers pending reservations AND confirmed visits.
ALTER TABLE clinic.appointment DROP CONSTRAINT appointment_doctor_no_overlap;
ALTER TABLE clinic.appointment ADD CONSTRAINT appointment_doctor_no_overlap EXCLUDE USING gist (
    (CASE WHEN status = 'PENDING' THEN reserved_doctor_id ELSE doctor_id END) WITH =,
    tstzrange(
        CASE WHEN status = 'PENDING' THEN reserved_start_at ELSE start_at END,
        clinic.plus_minutes(CASE WHEN status = 'PENDING' THEN reserved_end_at ELSE end_at END, buffer_minutes)
    ) WITH &&
) WHERE (status IN ('PENDING', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED'));

-- Once used, windows are immutable audit records. Add new windows for future dates;
-- never silently change the schedule underlying an existing patient's request.
CREATE FUNCTION clinic.protect_used_booking_window() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM clinic.appointment WHERE booking_window_id = OLD.id) THEN
        RAISE EXCEPTION 'A used booking window cannot be changed' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER protect_used_booking_window BEFORE UPDATE ON clinic.doctor_booking_window
    FOR EACH ROW EXECUTE FUNCTION clinic.protect_used_booking_window();
