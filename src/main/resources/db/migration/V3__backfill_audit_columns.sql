UPDATE organizations
SET created_at = COALESCE(created_at, api_key_created_at, NOW()),
    updated_at = COALESCE(updated_at, created_at, api_key_rotated_at, api_key_created_at, NOW())
WHERE created_at IS NULL
   OR updated_at IS NULL;

UPDATE users
SET created_at = COALESCE(created_at, NOW()),
    updated_at = COALESCE(updated_at, created_at, NOW())
WHERE created_at IS NULL
   OR updated_at IS NULL;

UPDATE events
SET created_at = COALESCE(created_at, occurred_at AT TIME ZONE 'UTC', NOW()),
    updated_at = COALESCE(updated_at, created_at, occurred_at AT TIME ZONE 'UTC', NOW())
WHERE created_at IS NULL
   OR updated_at IS NULL;
