-- Preferred doctor revision (PD-01 to PD-10, approved November 20, 2026).
-- The patient's optional, non-binding preference. Like the preferred date it is never
-- overwritten; reception still assigns doctor_id on confirm (BK-02).
ALTER TABLE clinic.appointment
    ADD COLUMN preferred_doctor_id uuid REFERENCES clinic.doctor(id) ON DELETE RESTRICT,
    ADD CONSTRAINT appointment_preferred_doctor_service_check
        CHECK (preferred_doctor_id IS NULL OR service_id IS NOT NULL);

CREATE INDEX appointment_preferred_doctor_idx ON clinic.appointment(preferred_doctor_id)
    WHERE preferred_doctor_id IS NOT NULL;
