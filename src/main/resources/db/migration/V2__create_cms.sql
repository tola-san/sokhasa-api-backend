CREATE TABLE clinic.cms_media (
    id uuid PRIMARY KEY, object_key varchar(255) NOT NULL UNIQUE,
    file_name varchar(255) NOT NULL, mime_type varchar(255) NOT NULL, size bigint NOT NULL
);
CREATE TABLE clinic.cms_pages (
    id uuid PRIMARY KEY, slug varchar(80) NOT NULL UNIQUE, revision bigint NOT NULL,
    created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, published_at timestamptz
);
CREATE TABLE clinic.cms_translations (
    page_id uuid NOT NULL REFERENCES clinic.cms_pages(id) ON DELETE CASCADE,
    position integer NOT NULL,
    language_code varchar(2) NOT NULL CHECK (language_code IN ('km','en','zh')),
    title varchar(160) NOT NULL, content text NOT NULL,
    media_id uuid REFERENCES clinic.cms_media(id), alt_text varchar(200),
    published boolean NOT NULL,
    PRIMARY KEY(page_id,position),
    UNIQUE(page_id,language_code,published) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX cms_translations_media_idx ON clinic.cms_translations(media_id);

CREATE TABLE clinic.cms_upload_cleanup (
    object_key varchar(255) PRIMARY KEY,
    retry_after timestamptz NOT NULL
);
