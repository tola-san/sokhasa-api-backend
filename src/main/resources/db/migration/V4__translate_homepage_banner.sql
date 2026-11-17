-- The homepage banner text is translated into Khmer, English and Chinese (approved November 17, 2026).
-- The image stays shared; heading, description and alt text move to one row per language.
CREATE TABLE clinic.cms_homepage_banner_translation (
    language_code varchar(2)   PRIMARY KEY CHECK (language_code IN ('km', 'en', 'zh')),
    heading       varchar(120) NOT NULL,
    description   varchar(300) NOT NULL,
    alt_text      varchar(200) NOT NULL
);

-- Keep the current English banner. Khmer and Chinese are left empty for an admin to add;
-- English is never copied into them as a placeholder.
INSERT INTO clinic.cms_homepage_banner_translation (language_code, heading, description, alt_text)
SELECT 'en', heading, description, alt_text
FROM clinic.cms_homepage_banner
WHERE revision > 0;

ALTER TABLE clinic.cms_homepage_banner DROP CONSTRAINT cms_homepage_banner_complete;
ALTER TABLE clinic.cms_homepage_banner
    DROP COLUMN heading,
    DROP COLUMN description,
    DROP COLUMN alt_text;
ALTER TABLE clinic.cms_homepage_banner ADD CONSTRAINT cms_homepage_banner_complete CHECK (
    (revision = 0 AND media_id IS NULL AND published_at IS NULL AND updated_at IS NULL)
    OR (revision > 0 AND media_id IS NOT NULL AND published_at IS NOT NULL AND updated_at IS NOT NULL)
);
