-- Public doctor profiles (PC-04 to PC-08) with card details and photos (DP-01 to DP-11),
-- revisions of November 19, 2026. Existing doctors stay non-public until an admin saves a profile.

ALTER TABLE clinic.doctor
    ADD COLUMN slug                varchar(80) UNIQUE
        CHECK (slug IS NULL OR slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    ADD COLUMN practice_start_year int CHECK (practice_start_year IS NULL OR practice_start_year >= 1950),
    ADD COLUMN publications_count  int CHECK (publications_count IS NULL OR publications_count BETWEEN 0 AND 10000),
    ADD COLUMN image_media_id      uuid REFERENCES clinic.cms_media (id);

CREATE INDEX doctor_image_media_idx ON clinic.doctor (image_media_id);

-- One row per language. A row exists only once the admin has saved that language's profile.
CREATE TABLE clinic.doctor_translation (
    doctor_id      uuid          NOT NULL REFERENCES clinic.doctor (id) ON DELETE CASCADE,
    language_code  varchar(2)    NOT NULL CHECK (language_code IN ('km', 'en', 'zh')),
    title          varchar(120)  NOT NULL CHECK (btrim(title) <> ''),
    bio            varchar(3000) CHECK (bio IS NULL OR btrim(bio) <> ''),
    education      varchar(500)  CHECK (education IS NULL OR btrim(education) <> ''),
    tags           text[]        NOT NULL DEFAULT '{}' CHECK (cardinality(tags) <= 5),
    image_alt_text varchar(200)  CHECK (image_alt_text IS NULL OR btrim(image_alt_text) <> ''),
    PRIMARY KEY (doctor_id, language_code)
);

-- Information only: a link does not create booking windows or limit reception's choice (PC-06).
CREATE TABLE clinic.service_doctor (
    service_id uuid NOT NULL REFERENCES clinic.service (id) ON DELETE RESTRICT,
    doctor_id  uuid NOT NULL REFERENCES clinic.doctor (id) ON DELETE RESTRICT,
    PRIMARY KEY (service_id, doctor_id)
);

CREATE INDEX service_doctor_doctor_idx ON clinic.service_doctor (doctor_id);
