UPDATE organizations
SET api_key_prefix = COALESCE(NULLIF(BTRIM(api_key_prefix), ''), LEFT(api_key_kid, 8), 'legacy')
WHERE api_key_prefix IS NULL
   OR BTRIM(api_key_prefix) = '';

ALTER TABLE organizations
    ALTER COLUMN api_key_prefix SET NOT NULL;
