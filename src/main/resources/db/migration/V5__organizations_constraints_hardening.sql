UPDATE organizations
SET name = CONCAT('org-', id)
WHERE name IS NULL
   OR BTRIM(name) = '';

UPDATE organizations
SET api_key_status = 'ACTIVE'
WHERE api_key_status IS NULL
   OR BTRIM(api_key_status) = '';

ALTER TABLE organizations
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN api_key_status SET DEFAULT 'ACTIVE',
    ALTER COLUMN api_key_status SET NOT NULL;
