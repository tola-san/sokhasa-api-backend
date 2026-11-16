-- The CMS page feature is replaced by the homepage hero banner (approved November 16, 2026).
DROP TABLE clinic.cms_translations;
DROP TABLE clinic.cms_pages;

-- Images uploaded for pages are no longer referenced. Queue their R2 objects for deletion by
-- the media cleanup job, then remove their metadata.
INSERT INTO clinic.cms_upload_cleanup(object_key, retry_after)
SELECT object_key, now() FROM clinic.cms_media
ON CONFLICT (object_key) DO UPDATE SET retry_after = excluded.retry_after;
DELETE FROM clinic.cms_media;

-- Exactly one banner row. Revision 0 means nothing has been published yet.
CREATE TABLE clinic.cms_homepage_banner (
    id smallint PRIMARY KEY CHECK (id = 1),
    revision bigint NOT NULL CHECK (revision >= 0),
    media_id uuid REFERENCES clinic.cms_media(id),
    alt_text varchar(200),
    heading varchar(80),
    description varchar(200),
    published_at timestamptz,
    updated_at timestamptz,
    CONSTRAINT cms_homepage_banner_complete CHECK (
        (revision = 0 AND media_id IS NULL AND alt_text IS NULL AND heading IS NULL
            AND description IS NULL AND published_at IS NULL AND updated_at IS NULL)
        OR (revision > 0 AND media_id IS NOT NULL AND alt_text IS NOT NULL AND heading IS NOT NULL
            AND description IS NOT NULL AND published_at IS NOT NULL AND updated_at IS NOT NULL)
    )
);
INSERT INTO clinic.cms_homepage_banner(id, revision) VALUES (1, 0);
