-- Dashboard statistics revision (DS-12, approved November 20, 2026): indexes for the grouped counts.

-- DS-03: requests created in a range, whatever their status. appointment_status_created_idx leads
-- with status, so it cannot serve a range across all statuses.
CREATE INDEX appointment_created_idx ON clinic.appointment (created_at);

-- DS-04: confirmations and completions recorded in a range.
CREATE INDEX appointment_history_status_changed_idx
    ON clinic.appointment_status_history (to_status, changed_at);

-- DS-08: today's booking windows for every doctor.
CREATE INDEX doctor_booking_window_day_idx ON clinic.doctor_booking_window (day, doctor_id);
