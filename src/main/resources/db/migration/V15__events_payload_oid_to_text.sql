ALTER TABLE events
ADD COLUMN payload_text TEXT;

UPDATE events
SET payload_text = CASE
    WHEN payload IS NULL THEN NULL
    ELSE convert_from(lo_get(payload), 'UTF8')
END;

ALTER TABLE events
DROP COLUMN payload;

ALTER TABLE events
RENAME COLUMN payload_text TO payload;
