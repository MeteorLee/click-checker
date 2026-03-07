UPDATE organizations
SET api_key_kid = COALESCE(
        NULLIF(BTRIM(api_key_kid), ''),
        CONCAT('legacy_', LPAD(TO_HEX(id), 24, '0'))
    ),
    api_key_hash = COALESCE(
        NULLIF(BTRIM(api_key_hash), ''),
        MD5(CONCAT('legacy-hash-a:', id)) || MD5(CONCAT('legacy-hash-b:', id))
    ),
    api_key_prefix = COALESCE(
        NULLIF(BTRIM(api_key_prefix), ''),
        LEFT(CONCAT('legacy_', LPAD(TO_HEX(id), 24, '0')), 8)
    ),
    api_key_status = 'DISABLED',
    api_key_created_at = COALESCE(api_key_created_at, NOW())
WHERE api_key_kid IS NULL
   OR BTRIM(api_key_kid) = ''
   OR api_key_hash IS NULL
   OR BTRIM(api_key_hash) = '';

ALTER TABLE organizations
    ALTER COLUMN api_key_kid SET NOT NULL,
    ALTER COLUMN api_key_hash SET NOT NULL;
