#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../common/db-lib.sh"

DATASET_META_PATH="${DATASET_META_PATH:-${1:-}}"

ROUTE_TEMPLATES=(
  "/home|home|100"
  "/pricing|pricing|95"
  "/docs|docs|90"
  "/blog|blog|85"
  "/signup|signup|80"
  "/login|login|80"
  "/dashboard|dashboard|75"
  "/settings|settings|75"
  "/projects/{id}|project|70"
  "/projects/{id}/overview|project_overview|70"
  "/projects/{id}/members|project_members|70"
  "/projects/{id}/events|project_events|70"
  "/projects/{id}/funnels|project_funnels|70"
  "/projects/{id}/retention|project_retention|70"
  "/projects/{id}/paths|project_paths|70"
  "/projects/{id}/routes|project_routes|70"
  "/teams/{id}|team|65"
  "/teams/{id}/members|team_members|65"
  "/teams/{id}/projects|team_projects|65"
  "/posts/{id}|post|60"
  "/posts/{id}/comments|post_comments|60"
  "/products/{id}|product|55"
  "/products/{id}/reviews|product_reviews|55"
  "/checkout|checkout|50"
)

MATCHED_RAW_PATHS=(
  "/home"
  "/pricing"
  "/docs"
  "/blog"
  "/signup"
  "/login"
  "/dashboard"
  "/settings"
  "/projects/1"
  "/projects/1/overview"
  "/projects/1/members"
  "/projects/1/events"
  "/projects/1/funnels"
  "/projects/1/retention"
  "/projects/1/paths"
  "/projects/1/routes"
  "/teams/1"
  "/teams/1/members"
  "/teams/1/projects"
  "/posts/10"
  "/posts/10/comments"
  "/products/8"
  "/products/8/reviews"
  "/checkout"
)

UNMATCHED_PATHS=(
  "/legacy/report/2024"
  "/debug/raw/1"
  "/temp/feature-x"
  "/unknown/path/a"
  "/misc/landing/1"
  "/preview/abc123"
)

EVENT_TYPE_MAPPINGS=(
  "page_view|view"
  "button_click|click"
  "signup_submit|signup"
  "purchase_complete|purchase"
)

RAW_EVENT_TYPES=(
  "page_view"
  "button_click"
  "signup_submit"
  "purchase_complete"
  "legacy_custom_event"
)

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

build_route_template_values() {
  local values=()
  local item template route_key priority escaped_template escaped_key

  for item in "${ROUTE_TEMPLATES[@]}"; do
    IFS='|' read -r template route_key priority <<<"${item}"
    escaped_template=${template//\'/\'\'}
    escaped_key=${route_key//\'/\'\'}
    values+=("(${org_id}, '${escaped_template}', '${escaped_key}', ${priority}, TIMESTAMPTZ '${range_from}', TIMESTAMPTZ '${range_from}')")
  done

  local IFS=$',\n'
  printf '%s' "${values[*]}"
}

build_event_type_mapping_values() {
  local values=()
  local item raw canonical escaped_raw escaped_canonical

  for item in "${EVENT_TYPE_MAPPINGS[@]}"; do
    IFS='|' read -r raw canonical <<<"${item}"
    escaped_raw=${raw//\'/\'\'}
    escaped_canonical=${canonical//\'/\'\'}
    values+=("(${org_id}, '${escaped_raw}', '${escaped_canonical}', TIMESTAMPTZ '${range_from}', TIMESTAMPTZ '${range_from}')")
  done

  local IFS=$',\n'
  printf '%s' "${values[*]}"
}

sql_text_array() {
  local -n source_ref=$1
  local values=()
  local value escaped

  for value in "${source_ref[@]}"; do
    escaped=${value//\'/\'\'}
    values+=("'${escaped}'")
  done

  local IFS=', '
  printf "%s" "${values[*]}"
}

restore_via_rds() {
  local sql_file="$1"
  run_psql_file_via_rds "${sql_file}"
}

main() {
  require_command jq
  require_command docker

  if [[ -z "${DATASET_META_PATH}" ]]; then
    echo "Usage: DATASET_META_PATH=artifacts/perf/prod-direct/m1/datasets/<version>/dataset.json scripts/perf/prod-direct/m1/restore-snapshot.sh" >&2
    exit 1
  fi

  if [[ ! -f "${DATASET_META_PATH}" ]]; then
    echo "Dataset metadata not found: ${DATASET_META_PATH}" >&2
    exit 1
  fi

  local org_id total_events identified_users identified_events anonymous_events
  local range_from range_to query_window_from query_window_to previous_window_from snapshot_version
  org_id=$(jq -r '.orgId' "${DATASET_META_PATH}")
  snapshot_version=$(jq -r '.snapshotVersion' "${DATASET_META_PATH}")
  total_events=$(jq -r '.totalEvents' "${DATASET_META_PATH}")
  identified_users=$(jq -r '.identifiedUsers' "${DATASET_META_PATH}")
  identified_events=$(jq -r '.identifiedEvents' "${DATASET_META_PATH}")
  anonymous_events=$(jq -r '.anonymousEvents' "${DATASET_META_PATH}")
  range_from=$(jq -r '.rangeFrom' "${DATASET_META_PATH}")
  range_to=$(jq -r '.rangeTo' "${DATASET_META_PATH}")
  query_window_from=$(jq -r '.queryWindowFrom' "${DATASET_META_PATH}")
  query_window_to=$(jq -r '.queryWindowTo' "${DATASET_META_PATH}")
  previous_window_from=$(date -u -d "${query_window_to} -2 day" +"%Y-%m-%dT%H:%M:%SZ")

  if [[ "${anonymous_events}" -ne $((total_events - identified_events)) ]]; then
    echo "Dataset metadata is inconsistent: anonymousEvents must equal totalEvents - identifiedEvents" >&2
    exit 1
  fi

  local hot_paths_sql matched_paths_sql unmatched_paths_sql raw_event_types_sql sql_file
  local route_template_values event_type_mapping_values
  hot_paths_sql=$(sql_text_array MATCHED_RAW_PATHS)
  matched_paths_sql=$(sql_text_array MATCHED_RAW_PATHS)
  unmatched_paths_sql=$(sql_text_array UNMATCHED_PATHS)
  raw_event_types_sql=$(sql_text_array RAW_EVENT_TYPES)
  route_template_values=$(build_route_template_values)
  event_type_mapping_values=$(build_event_type_mapping_values)

  sql_file=$(mktemp)
  cat > "${sql_file}" <<SQL
BEGIN;

DELETE FROM events WHERE organization_id = ${org_id};
DELETE FROM users WHERE organization_id = ${org_id};
DELETE FROM route_templates WHERE organization_id = ${org_id};
DELETE FROM event_type_mappings WHERE organization_id = ${org_id};

INSERT INTO route_templates (organization_id, template, route_key, priority, created_at, updated_at)
VALUES
${route_template_values};

INSERT INTO event_type_mappings (organization_id, raw_event_type, canonical_event_type, created_at, updated_at)
VALUES
${event_type_mapping_values};

INSERT INTO users (organization_id, external_user_id, created_at, updated_at)
SELECT
  ${org_id},
  'm1-user-' || lpad(gs::text, 4, '0'),
  TIMESTAMPTZ '${range_from}',
  TIMESTAMPTZ '${range_from}'
FROM generate_series(1, ${identified_users}) AS gs;

WITH constants AS (
  SELECT
    ARRAY[${hot_paths_sql}]::text[] AS hot_paths,
    ARRAY[${matched_paths_sql}]::text[] AS matched_paths,
    ARRAY[${unmatched_paths_sql}]::text[] AS unmatched_paths,
    ARRAY[${raw_event_types_sql}]::text[] AS raw_event_types
),
generated_events AS (
  SELECT
    gs AS seq,
    CASE
      WHEN gs <= 20000 THEN TIMESTAMPTZ '${range_from}' + (((gs - 1) % (5 * 24 * 60 * 60)) * INTERVAL '1 second')
      WHEN gs <= 32000 THEN TIMESTAMPTZ '${previous_window_from}' + (((gs - 20001) % (24 * 60 * 60)) * INTERVAL '1 second')
      ELSE TIMESTAMPTZ '${query_window_from}' + (((gs - 32001) % (24 * 60 * 60)) * INTERVAL '1 second')
    END AS occurred_at,
    CASE
      WHEN gs <= ${identified_events} THEN
        CASE
          WHEN gs % 100 < 70 THEN 'm1-user-' || lpad((((gs - 1) % 100) + 1)::text, 4, '0')
          ELSE 'm1-user-' || lpad((101 + ((gs - 1) % 900))::text, 4, '0')
        END
      ELSE NULL
    END AS external_user_id,
    CASE
      WHEN gs % 100 < 55 THEN
        c.hot_paths[((gs - 1) % 6) + 1]
      WHEN gs % 100 < 85 THEN
        c.matched_paths[7 + ((gs - 1) % 18)]
      ELSE
        c.unmatched_paths[((gs - 1) % 6) + 1]
    END AS path,
    CASE
      WHEN gs % 100 < 45 THEN c.raw_event_types[1]
      WHEN gs % 100 < 75 THEN c.raw_event_types[2]
      WHEN gs % 100 < 90 THEN c.raw_event_types[3]
      WHEN gs % 100 < 95 THEN c.raw_event_types[4]
      ELSE c.raw_event_types[5]
    END AS event_type
  FROM generate_series(1, ${total_events}) AS gs
  CROSS JOIN constants AS c
),
resolved_events AS (
  SELECT
    g.seq,
    g.occurred_at,
    g.path,
    g.event_type,
    u.id AS event_user_id
  FROM generated_events AS g
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

COMMIT;
SQL

  echo "[m1-restore] restore snapshot ${snapshot_version}"
  restore_via_rds "${sql_file}"
  rm -f "${sql_file}"
}

main "$@"
