ALTER TABLE clinic.doctor ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE clinic.doctor_booking_window ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- Also protect windows used as the destination of a receptionist's changed confirmation.
CREATE OR REPLACE FUNCTION clinic.protect_used_booking_window() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM clinic.appointment a
        WHERE a.booking_window_id = OLD.id
           OR (a.doctor_id = OLD.doctor_id AND a.service_id = OLD.service_id
               AND a.status IN ('CONFIRMED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED')
               AND tstzrange(a.start_at, clinic.plus_minutes(a.end_at, a.buffer_minutes)) &&
                   tstzrange((OLD.day + OLD.start_time) AT TIME ZONE 'Asia/Phnom_Penh',
                             (OLD.day + OLD.end_time) AT TIME ZONE 'Asia/Phnom_Penh'))
    ) THEN
        RAISE EXCEPTION 'A used booking window cannot be changed' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER protect_deleted_booking_window BEFORE DELETE ON clinic.doctor_booking_window
    FOR EACH ROW EXECUTE FUNCTION clinic.protect_used_booking_window();
