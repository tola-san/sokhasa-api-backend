-- Bookable clinic services and their categories, each translated into Khmer, English and Chinese
-- (public service list approved November 18, 2026; tables from DATABASE-DESIGN.md "Service tables").

CREATE TABLE clinic.service_category (
    id         uuid         PRIMARY KEY,
    slug       varchar(100) NOT NULL UNIQUE,
    sort_order int          NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE clinic.service_category_translation (
    category_id   uuid         NOT NULL REFERENCES clinic.service_category (id) ON DELETE CASCADE,
    language_code varchar(2)   NOT NULL CHECK (language_code IN ('km', 'en', 'zh')),
    name          varchar(120) NOT NULL CHECK (btrim(name) <> ''),
    PRIMARY KEY (category_id, language_code)
);

CREATE TABLE clinic.service (
    id                 uuid          PRIMARY KEY,
    slug               varchar(100)  NOT NULL UNIQUE,
    category_id        uuid          NOT NULL REFERENCES clinic.service_category (id),
    duration_minutes   int           NOT NULL CHECK (duration_minutes BETWEEN 5 AND 480),
    buffer_minutes     int           NOT NULL DEFAULT 0 CHECK (buffer_minutes BETWEEN 0 AND 60),
    price_usd          numeric(10,2) CHECK (price_usd >= 0),
    original_price_usd numeric(10,2),
    status             varchar(10)   NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    image_media_id     uuid          REFERENCES clinic.cms_media (id),
    sort_order         int           NOT NULL DEFAULT 0,
    version            bigint        NOT NULL DEFAULT 0,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    -- A promotion price needs a current price and must be higher than it.
    CONSTRAINT service_original_price_check CHECK (
        original_price_usd IS NULL OR (price_usd IS NOT NULL AND original_price_usd > price_usd)
    )
);

CREATE INDEX service_category_idx ON clinic.service (category_id);
CREATE INDEX service_image_media_idx ON clinic.service (image_media_id);

CREATE TABLE clinic.service_translation (
    service_id        uuid         NOT NULL REFERENCES clinic.service (id) ON DELETE CASCADE,
    language_code     varchar(2)   NOT NULL CHECK (language_code IN ('km', 'en', 'zh')),
    name              varchar(120) NOT NULL CHECK (btrim(name) <> ''),
    short_description varchar(300) NOT NULL CHECK (btrim(short_description) <> ''),
    image_alt_text    varchar(200) CHECK (image_alt_text IS NULL OR btrim(image_alt_text) <> ''),
    PRIMARY KEY (service_id, language_code)
);
