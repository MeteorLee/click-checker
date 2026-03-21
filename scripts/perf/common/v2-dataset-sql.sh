#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/v2-dataset-lib.sh"

v2_sql_literal() {
  local value="$1"
  value=${value//\'/\'\'}
  printf "'%s'" "${value}"
}

v2_sql_text_array() {
  local -n source_ref=$1
  local values=()
  local value

  for value in "${source_ref[@]}"; do
    values+=("$(v2_sql_literal "${value}")")
  done

  local IFS=', '
  printf "%s" "${values[*]}"
}

v2_build_route_template_values_for_org() {
  local org_id="$1"
  local created_at="$2"
  local values=()
  local item template route_key priority

  for item in "${V2_ROUTE_TEMPLATES[@]}"; do
    IFS='|' read -r template route_key priority <<<"${item}"
    values+=("(${org_id}, $(v2_sql_literal "${template}"), $(v2_sql_literal "${route_key}"), ${priority}, TIMESTAMPTZ $(v2_sql_literal "${created_at}"), TIMESTAMPTZ $(v2_sql_literal "${created_at}"))")
  done

  local IFS=$',\n'
  printf '%s' "${values[*]}"
}

v2_build_event_type_mapping_values_for_org() {
  local org_id="$1"
  local created_at="$2"
  local values=()
  local item raw_event_type canonical_event_type

  for item in "${V2_EVENT_TYPE_MAPPINGS[@]}"; do
    IFS='|' read -r raw_event_type canonical_event_type <<<"${item}"
    values+=("(${org_id}, $(v2_sql_literal "${raw_event_type}"), $(v2_sql_literal "${canonical_event_type}"), TIMESTAMPTZ $(v2_sql_literal "${created_at}"), TIMESTAMPTZ $(v2_sql_literal "${created_at}"))")
  done

  local IFS=$',\n'
  printf '%s' "${values[*]}"
}

v2_append_org_snapshot_sql() {
  local output_path="$1"
  local org_key="$2"
  local org_id="$3"
  local total_events="$4"
  local identified_users="$5"
  local created_at="$6"
  local seed_anchor_at="$7"

  local identified_events=$(( total_events * 70 / 100 ))
  local anonymous_events=$(( total_events - identified_events ))
  local existing_identified_events_target=$(( identified_events * 80 / 100 ))
  local existing_identified_users=$(( identified_users * 80 / 100 ))
  local new_identified_users=$(( identified_users - existing_identified_users ))
  local recent_events=$(( total_events * 50 / 100 ))
  local mid_events=$(( total_events * 30 / 100 ))
  local historic_events=$(( total_events - recent_events - mid_events ))
  local funnel_success_users=$(( identified_users / 100 ))
  local funnel_signup_drop_users=$(( identified_users / 150 ))
  local funnel_click_drop_users=$(( identified_users / 150 ))
  local retention_pattern_users=$(( identified_users / 20 ))
  local reserved_existing_users

  if [[ "${existing_identified_users}" -le 0 ]]; then
    existing_identified_users=1
  fi
  if [[ "${new_identified_users}" -le 0 ]]; then
    new_identified_users=1
  fi
  if [[ "${historic_events}" -le 0 ]]; then
    historic_events=1
  fi
  if [[ "${funnel_success_users}" -lt 12 ]]; then
    funnel_success_users=12
  fi
  if [[ "${funnel_signup_drop_users}" -lt 8 ]]; then
    funnel_signup_drop_users=8
  fi
  if [[ "${funnel_click_drop_users}" -lt 8 ]]; then
    funnel_click_drop_users=8
  fi
  if [[ "${retention_pattern_users}" -lt 24 ]]; then
    retention_pattern_users=24
  fi

  reserved_existing_users=$(( funnel_success_users + funnel_signup_drop_users + funnel_click_drop_users + retention_pattern_users ))
  if [[ "${reserved_existing_users}" -ge "${existing_identified_users}" ]]; then
    reserved_existing_users=$(( existing_identified_users - 1 ))
    funnel_success_users=$(( reserved_existing_users / 4 ))
    funnel_signup_drop_users=$(( reserved_existing_users / 6 ))
    funnel_click_drop_users=$(( reserved_existing_users / 6 ))
    retention_pattern_users=$(( reserved_existing_users - funnel_success_users - funnel_signup_drop_users - funnel_click_drop_users ))
  fi

  cat >> "${output_path}" <<SQL
-- org ${org_key}
DELETE FROM events WHERE organization_id = ${org_id};
DELETE FROM users WHERE organization_id = ${org_id};
DELETE FROM route_templates WHERE organization_id = ${org_id};
DELETE FROM event_type_mappings WHERE organization_id = ${org_id};

INSERT INTO route_templates (organization_id, template, route_key, priority, created_at, updated_at)
VALUES
$(v2_build_route_template_values_for_org "${org_id}" "${created_at}");

INSERT INTO event_type_mappings (organization_id, raw_event_type, canonical_event_type, created_at, updated_at)
VALUES
$(v2_build_event_type_mapping_values_for_org "${org_id}" "${created_at}");

INSERT INTO users (organization_id, external_user_id, created_at, updated_at)
SELECT
  ${org_id},
  $(v2_sql_literal "${org_key}") || '-user-' || lpad(gs::text, 5, '0'),
  TIMESTAMPTZ $(v2_sql_literal "${created_at}"),
  TIMESTAMPTZ $(v2_sql_literal "${created_at}")
FROM generate_series(1, ${identified_users}) AS gs;

WITH constants AS (
  SELECT
    ARRAY[$(v2_sql_text_array V2_BROWSE_PATHS)]::text[] AS browse_paths,
    ARRAY[$(v2_sql_text_array V2_PRODUCT_PATHS)]::text[] AS product_paths,
    ARRAY[$(v2_sql_text_array V2_CONVERSION_PATHS)]::text[] AS conversion_paths,
    TIMESTAMPTZ $(v2_sql_literal "${seed_anchor_at}") AS seed_anchor_at,
    $(v2_sql_literal "${org_key}") AS org_key,
    ${identified_events}::integer AS identified_events_target,
    ${existing_identified_events_target}::integer AS existing_identified_events_target,
    ${anonymous_events}::integer AS anonymous_events_target,
    ${existing_identified_users}::integer AS existing_identified_users,
    ${new_identified_users}::integer AS new_identified_users,
    ${reserved_existing_users}::integer AS reserved_existing_users
),
funnel_success_users AS (
  SELECT
    gs AS user_seq,
    c.org_key || '-user-' || lpad(gs::text, 5, '0') AS external_user_id,
    date_trunc('day', c.seed_anchor_at)
      - ((((gs - 1) % 14) + 1) * INTERVAL '1 day')
      + INTERVAL '10 hour'
      + (((gs * 631) % 5400) * INTERVAL '1 second') AS anchor_at
  FROM generate_series(1, ${funnel_success_users}) AS gs
  CROSS JOIN constants AS c
),
funnel_signup_drop_users AS (
  SELECT
    gs AS user_seq,
    c.org_key || '-user-' || lpad((${funnel_success_users} + gs)::text, 5, '0') AS external_user_id,
    date_trunc('day', c.seed_anchor_at)
      - ((((gs - 1) % 14) + 1) * INTERVAL '1 day')
      + INTERVAL '12 hour'
      + (((gs * 379) % 5400) * INTERVAL '1 second') AS anchor_at
  FROM generate_series(1, ${funnel_signup_drop_users}) AS gs
  CROSS JOIN constants AS c
),
funnel_click_drop_users AS (
  SELECT
    gs AS user_seq,
    c.org_key || '-user-' || lpad((${funnel_success_users} + ${funnel_signup_drop_users} + gs)::text, 5, '0') AS external_user_id,
    date_trunc('day', c.seed_anchor_at)
      - ((((gs - 1) % 10) + 1) * INTERVAL '1 day')
      + INTERVAL '14 hour'
      + (((gs * 281) % 3600) * INTERVAL '1 second') AS anchor_at
  FROM generate_series(1, ${funnel_click_drop_users}) AS gs
  CROSS JOIN constants AS c
),
retention_pattern_users AS (
  SELECT
    gs AS user_seq,
    c.org_key || '-user-' || lpad((${funnel_success_users} + ${funnel_signup_drop_users} + ${funnel_click_drop_users} + gs)::text, 5, '0') AS external_user_id,
    CASE ((gs - 1) % 7)
      WHEN 0 THEN 30
      WHEN 1 THEN 21
      WHEN 2 THEN 14
      WHEN 3 THEN 7
      WHEN 4 THEN 1
      WHEN 5 THEN 45
      ELSE 60
    END AS cohort_day_offset,
    c.seed_anchor_at
  FROM generate_series(1, ${retention_pattern_users}) AS gs
  CROSS JOIN constants AS c
),
retention_users AS (
  SELECT
    user_seq,
    external_user_id,
    cohort_day_offset,
    date_trunc('day', seed_anchor_at)
      - (cohort_day_offset * INTERVAL '1 day')
      + INTERVAL '11 hour'
      + (((user_seq * 353) % 5400) * INTERVAL '1 second') AS first_seen_at
  FROM retention_pattern_users
),
special_events AS (
  SELECT
    (user_seq * 10) + 1 AS seq,
    anchor_at AS occurred_at,
    '/pricing'::text AS path,
    'page_view'::text AS event_type,
    external_user_id,
    'existing'::text AS user_bucket
  FROM funnel_success_users
  UNION ALL
  SELECT
    (user_seq * 10) + 2,
    anchor_at + INTERVAL '45 minute' + (((user_seq * 19) % 900) * INTERVAL '1 second'),
    '/signup',
    'signup_submit',
    external_user_id,
    'existing'
  FROM funnel_success_users
  UNION ALL
  SELECT
    (user_seq * 10) + 3,
    anchor_at + INTERVAL '1 day' + INTERVAL '2 hour' + (((user_seq * 23) % 1800) * INTERVAL '1 second'),
    '/checkout',
    'purchase_complete',
    external_user_id,
    'existing'
  FROM funnel_success_users
  UNION ALL
  SELECT
    200000 + (user_seq * 10) + 1,
    anchor_at,
    '/pricing',
    'page_view',
    external_user_id,
    'existing'
  FROM funnel_signup_drop_users
  UNION ALL
  SELECT
    200000 + (user_seq * 10) + 2,
    anchor_at + INTERVAL '35 minute' + (((user_seq * 17) % 900) * INTERVAL '1 second'),
    '/signup',
    'signup_submit',
    external_user_id,
    'existing'
  FROM funnel_signup_drop_users
  UNION ALL
  SELECT
    400000 + (user_seq * 10) + 1,
    anchor_at,
    '/pricing',
    'page_view',
    external_user_id,
    'existing'
  FROM funnel_click_drop_users
  UNION ALL
  SELECT
    400000 + (user_seq * 10) + 2,
    anchor_at + INTERVAL '10 minute' + (((user_seq * 13) % 600) * INTERVAL '1 second'),
    '/pricing',
    'button_click',
    external_user_id,
    'existing'
  FROM funnel_click_drop_users
  UNION ALL
  SELECT
    600000 + (user_seq * 10) + 1,
    first_seen_at,
    '/dashboard',
    'page_view',
    external_user_id,
    'existing'
  FROM retention_users
  UNION ALL
  SELECT
    600000 + (user_seq * 10) + 2,
    first_seen_at + INTERVAL '1 day' + INTERVAL '10 minute',
    '/dashboard',
    'button_click',
    external_user_id,
    'existing'
  FROM retention_users
  WHERE user_seq % 100 < 65
    AND cohort_day_offset >= 1
  UNION ALL
  SELECT
    600000 + (user_seq * 10) + 3,
    first_seen_at + INTERVAL '7 day' + INTERVAL '20 minute',
    '/projects/1/overview',
    'page_view',
    external_user_id,
    'existing'
  FROM retention_users
  WHERE user_seq % 100 < 45
    AND cohort_day_offset >= 7
  UNION ALL
  SELECT
    600000 + (user_seq * 10) + 4,
    first_seen_at + INTERVAL '14 day' + INTERVAL '30 minute',
    '/projects/1/routes',
    'button_click',
    external_user_id,
    'existing'
  FROM retention_users
  WHERE user_seq % 100 < 25
    AND cohort_day_offset >= 14
  UNION ALL
  SELECT
    600000 + (user_seq * 10) + 5,
    first_seen_at + INTERVAL '30 day' + INTERVAL '40 minute',
    '/projects/1/events',
    'page_view',
    external_user_id,
    'existing'
  FROM retention_users
  WHERE user_seq % 100 < 12
    AND cohort_day_offset >= 30
),
special_counts AS (
  SELECT
    count(*) AS total_special_events,
    count(*) FILTER (WHERE external_user_id IS NOT NULL) AS special_identified_events,
    count(*) FILTER (WHERE user_bucket = 'existing') AS special_existing_events
  FROM special_events
),
generic_seed AS (
  SELECT
    gs AS seq,
    CASE
      WHEN gs <= ${recent_events} THEN
        date_trunc('day', c.seed_anchor_at)
        - (((gs - 1) % 7) * INTERVAL '1 day')
      WHEN gs <= $((recent_events + mid_events)) THEN
        date_trunc('day', c.seed_anchor_at)
        - INTERVAL '8 day'
        - (((gs - ${recent_events} - 1) % 23) * INTERVAL '1 day')
      ELSE
        date_trunc('day', c.seed_anchor_at)
        - INTERVAL '31 day'
        - (((gs - ${recent_events} - ${mid_events} - 1) % 60) * INTERVAL '1 day')
    END
    +
    CASE
      WHEN gs % 100 < 60 THEN INTERVAL '9 hour' + (((gs * 37) % 32400) * INTERVAL '1 second')
      WHEN gs % 100 < 85 THEN INTERVAL '18 hour' + (((gs * 53) % 21600) * INTERVAL '1 second')
      ELSE (((gs * 71) % 32400) * INTERVAL '1 second')
    END AS occurred_at,
    CASE
      WHEN gs <= (c.existing_identified_events_target - sc.special_existing_events) THEN
        c.org_key || '-user-' || lpad((c.reserved_existing_users + (((gs - 1) % GREATEST(c.existing_identified_users - c.reserved_existing_users, 1)) + 1))::text, 5, '0')
      WHEN gs <= (c.identified_events_target - sc.special_identified_events) THEN
        c.org_key || '-user-' || lpad((c.existing_identified_users + (((gs - (c.existing_identified_events_target - sc.special_existing_events) - 1) % GREATEST(c.new_identified_users, 1)) + 1))::text, 5, '0')
      ELSE NULL
    END AS external_user_id,
    CASE
      WHEN gs % 100 < 60 THEN 'browse'
      WHEN gs % 100 < 85 THEN 'product'
      ELSE 'conversion'
    END AS path_group,
    ((gs * 29) % 100) AS event_pct
  FROM generate_series(1, ${total_events} - (SELECT total_special_events FROM special_counts)) AS gs
  CROSS JOIN constants AS c
  CROSS JOIN special_counts AS sc
),
generic_events AS (
  SELECT
    seq,
    occurred_at,
    external_user_id,
    CASE path_group
      WHEN 'browse' THEN c.browse_paths[((seq - 1) % ${#V2_BROWSE_PATHS[@]}) + 1]
      WHEN 'product' THEN c.product_paths[((seq - 1) % ${#V2_PRODUCT_PATHS[@]}) + 1]
      ELSE c.conversion_paths[((seq - 1) % ${#V2_CONVERSION_PATHS[@]}) + 1]
    END AS path,
    CASE
      WHEN path_group = 'browse' AND event_pct < 70 THEN 'page_view'
      WHEN path_group = 'browse' AND event_pct < 95 THEN 'button_click'
      WHEN path_group = 'browse' AND event_pct < 99 THEN 'signup_submit'
      WHEN path_group = 'browse' THEN 'purchase_complete'
      WHEN path_group = 'product' AND event_pct < 45 THEN 'page_view'
      WHEN path_group = 'product' AND event_pct < 80 THEN 'button_click'
      WHEN path_group = 'product' AND event_pct < 93 THEN 'signup_submit'
      WHEN path_group = 'product' THEN 'purchase_complete'
      WHEN path_group = 'conversion' AND event_pct < 20 THEN 'page_view'
      WHEN path_group = 'conversion' AND event_pct < 45 THEN 'button_click'
      WHEN path_group = 'conversion' AND event_pct < 80 THEN 'signup_submit'
      ELSE 'purchase_complete'
    END AS event_type
  FROM generic_seed AS g
  CROSS JOIN constants AS c
),
all_events AS (
  SELECT seq, occurred_at, path, event_type, external_user_id
  FROM special_events
  UNION ALL
  SELECT 900000 + seq, occurred_at, path, event_type, external_user_id
  FROM generic_events
),
resolved_events AS (
  SELECT
    g.seq,
    g.occurred_at,
    g.path,
    g.event_type,
    u.id AS event_user_id
  FROM all_events AS g
  LEFT JOIN users AS u
    ON u.organization_id = ${org_id}
   AND u.external_user_id = g.external_user_id
)
INSERT INTO events (
  event_type,
  occurred_at,
  path,
  payload,
  event_user_id,
  organization_id,
  created_at,
  updated_at
)
SELECT
  event_type,
  occurred_at,
  path,
  NULL,
  event_user_id,
  ${org_id},
  occurred_at,
  occurred_at
FROM resolved_events
ORDER BY seq;

-- org ${org_key} summary:
-- total_events=${total_events}, identified_events=${identified_events}, anonymous_events=${anonymous_events}
-- scripted_users: funnel_success=${funnel_success_users}, funnel_signup_drop=${funnel_signup_drop_users}, funnel_click_drop=${funnel_click_drop_users}, retention=${retention_pattern_users}

SQL
}

v2_generate_snapshot_sql() {
  local meta_path="$1"
  local output_path="$2"
  local created_at seed_anchor_at

  v2_verify_dataset_meta_contract "${meta_path}"
  created_at=$(v2_load_dataset_meta_value "${meta_path}" '.createdAt')
  seed_anchor_at=$(v2_load_dataset_meta_value "${meta_path}" '.seedAnchorAt')

  : > "${output_path}"
  cat > "${output_path}" <<SQL
BEGIN;

SQL

  while IFS=$'\t' read -r org_key org_id total_events identified_users; do
    v2_append_org_snapshot_sql \
      "${output_path}" \
      "${org_key}" \
      "${org_id}" \
      "${total_events}" \
      "${identified_users}" \
      "${created_at}" \
      "${seed_anchor_at}"
  done < <(jq -r '.orgs[] | [.key, .orgId, .totalEvents, .identifiedUsers] | @tsv' "${meta_path}")

  cat >> "${output_path}" <<'SQL'
COMMIT;
SQL
}

v2_quick_reset_sql_path_from_meta_path() {
  local meta_path="$1"
  printf '%s/quick-reset.sql\n' "$(dirname "${meta_path}")"
}

v2_append_org_quick_reset_sql() {
  local output_path="$1"
  local org_key="$2"
  local org_id="$3"

  cat >> "${output_path}" <<SQL
-- quick reset org ${org_key}
DELETE FROM events
WHERE organization_id = ${org_id}
  AND (
    (payload IS NOT NULL AND convert_from(lo_get(payload), 'UTF8') LIKE '%"source":"k6-v2"%')
    OR event_user_id IN (
      SELECT id
      FROM users
      WHERE organization_id = ${org_id}
        AND external_user_id LIKE $(v2_sql_literal "${org_key}-new-%")
    )
  );

DELETE FROM users
WHERE organization_id = ${org_id}
  AND external_user_id LIKE $(v2_sql_literal "${org_key}-new-%");

SQL
}

v2_generate_quick_reset_sql() {
  local meta_path="$1"
  local output_path="$2"

  v2_verify_dataset_meta_contract "${meta_path}"

  : > "${output_path}"
  cat > "${output_path}" <<SQL
BEGIN;

SQL

  while IFS=$'\t' read -r org_key org_id; do
    v2_append_org_quick_reset_sql \
      "${output_path}" \
      "${org_key}" \
      "${org_id}"
  done < <(jq -r '.orgs[] | [.key, .orgId] | @tsv' "${meta_path}")

  cat >> "${output_path}" <<'SQL'
COMMIT;
SQL
}
