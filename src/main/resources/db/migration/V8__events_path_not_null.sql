UPDATE events
SET path = '/legacy'
WHERE path IS NULL
   OR BTRIM(path) = '';

ALTER TABLE events
    ALTER COLUMN path SET NOT NULL;
