-- V2: Create all clinic domain tables

-- 1. Patients
CREATE TABLE IF NOT EXISTS clinic.patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_patients_phone ON clinic.patients (phone);
CREATE INDEX IF NOT EXISTS idx_patients_full_name ON clinic.patients (full_name);

-- 2. Doctors
CREATE TABLE IF NOT EXISTS clinic.doctors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    profile_image_id UUID,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doctors_status ON clinic.doctors (status);

-- 3. Doctor Schedules
CREATE TABLE IF NOT EXISTS clinic.doctor_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id UUID NOT NULL REFERENCES clinic.doctors(id) ON DELETE CASCADE,
    day_of_week VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    effective_from DATE NOT NULL,
    effective_until DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doctor_schedules_lookup ON clinic.doctor_schedules (doctor_id, day_of_week);

-- 4. Services
CREATE TABLE IF NOT EXISTS clinic.services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    duration_minutes INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_services_status ON clinic.services (status);

-- 5. Service Translations
CREATE TABLE IF NOT EXISTS clinic.service_translations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id UUID NOT NULL REFERENCES clinic.services(id) ON DELETE CASCADE,
    language_code VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    CONSTRAINT uq_service_language UNIQUE (service_id, language_code)
);

CREATE INDEX IF NOT EXISTS idx_service_translations_lang ON clinic.service_translations (language_code);

-- 6. Appointments
CREATE TABLE IF NOT EXISTS clinic.appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(50) NOT NULL UNIQUE,
    patient_id UUID NOT NULL REFERENCES clinic.patients(id),
    service_id UUID NOT NULL REFERENCES clinic.services(id),
    preferred_doctor_id UUID REFERENCES clinic.doctors(id),
    assigned_doctor_id UUID REFERENCES clinic.doctors(id),
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_appointments_date_doctor ON clinic.appointments (appointment_date, assigned_doctor_id, status);
CREATE INDEX IF NOT EXISTS idx_appointments_status ON clinic.appointments (status);
CREATE INDEX IF NOT EXISTS idx_appointments_reference ON clinic.appointments (reference);

-- 7. Appointment Status History
CREATE TABLE IF NOT EXISTS clinic.appointment_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID NOT NULL REFERENCES clinic.appointments(id) ON DELETE CASCADE,
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_by_user_id VARCHAR(100),
    changed_by_username VARCHAR(100),
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_appointment_status_history_appt ON clinic.appointment_status_history (appointment_id);

-- 8. Pages (CMS)
CREATE TABLE IF NOT EXISTS clinic.pages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 1,
    published_language_codes VARCHAR(10)[] NOT NULL DEFAULT '{}',
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pages_slug ON clinic.pages (slug);
CREATE INDEX IF NOT EXISTS idx_pages_status ON clinic.pages (status);

-- 9. Page Translations
CREATE TABLE IF NOT EXISTS clinic.page_translations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id UUID NOT NULL REFERENCES clinic.pages(id) ON DELETE CASCADE,
    language_code VARCHAR(10) NOT NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    media_id UUID,
    alt_text VARCHAR(200),
    published_title VARCHAR(160),
    published_content TEXT,
    published_media_id UUID,
    published_alt_text VARCHAR(200),
    CONSTRAINT uq_page_language UNIQUE (page_id, language_code)
);

CREATE INDEX IF NOT EXISTS idx_page_translations_page ON clinic.page_translations (page_id);

-- 10. Media
CREATE TABLE IF NOT EXISTS clinic.media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 11. Staff
CREATE TABLE IF NOT EXISTS clinic.staff (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id VARCHAR(100) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    roles VARCHAR(50)[] NOT NULL DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_staff_username ON clinic.staff (username);

-- 12. Clinic Settings
CREATE TABLE IF NOT EXISTS clinic.clinic_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL DEFAULT 'Sokhasa Clinic',
    phone VARCHAR(50),
    email VARCHAR(255),
    address TEXT,
    timezone VARCHAR(100) NOT NULL DEFAULT 'Asia/Phnom_Penh',
    supported_languages VARCHAR(10)[] NOT NULL DEFAULT '{"km","en","zh"}',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 13. Clinic Working Hours
CREATE TABLE IF NOT EXISTS clinic.clinic_working_hours (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    day_of_week VARCHAR(20) NOT NULL UNIQUE,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE
);

-- 14. Notification Outbox (for Telegram and other asynchronous events)
CREATE TABLE IF NOT EXISTS clinic.notification_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_retry ON clinic.notification_outbox (status, next_retry_at);
