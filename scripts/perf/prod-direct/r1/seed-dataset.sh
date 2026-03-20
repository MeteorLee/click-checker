#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../common/db-lib.sh"

PREPARE_BASE_URL="${PREPARE_BASE_URL:-http://localhost:8080}"
DATASET_VERSION="${DATASET_VERSION:-r1-v1}"
DATASET_DIR="${DATASET_DIR:-artifacts/perf/prod-direct/r1/datasets/${DATASET_VERSION}}"
DATASET_META_PATH="${DATASET_META_PATH:-${DATASET_DIR}/dataset.json}"
ORG_ID="${ORG_ID:-}"
ORG_NAME="${ORG_NAME:-perf-r1-dataset}"
API_KEY="${API_KEY:-}"
API_KEY_PREFIX="${API_KEY_PREFIX:-}"
TOTAL_EVENTS="${TOTAL_EVENTS:-50000}"
IDENTIFIED_USERS="${IDENTIFIED_USERS:-1000}"
IDENTIFIED_EVENTS="${IDENTIFIED_EVENTS:-40000}"
ANONYMOUS_EVENTS="${ANONYMOUS_EVENTS:-10000}"
CAPTURE_TIME_RANGE="${CAPTURE_TIME_RANGE:-start-2m ~ end+2m}"

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

DATASET_CREATED="false"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

cleanup_dataset_dir() {
  local exit_code=$?

  if [[ "${DATASET_CREATED}" != "true" && -d "${DATASET_DIR}" ]]; then
    rm -rf "${DATASET_DIR}"
  fi

  exit "${exit_code}"
}

post_json() {
  local url="$1"
  local body="$2"
  shift 2

  local response
  response=$(mktemp)

  local status
  status=$(curl -sS -o "${response}" -w "%{http_code}" \
    -H "Content-Type: application/json" \
    "$@" \
    -X POST "${url}" \
    -d "${body}")

  if [[ "${status}" != "200" && "${status}" != "201" ]]; then
    echo "Request failed: POST ${url} -> ${status}" >&2
    cat "${response}" >&2 || true
    rm -f "${response}"
    exit 1
  fi

  cat "${response}"
  rm -f "${response}"
}

create_route_templates() {
  local template route_key priority body
  for item in "${ROUTE_TEMPLATES[@]}"; do
    IFS='|' read -r template route_key priority <<<"${item}"
    body=$(jq -nc \
      --arg template "${template}" \
      --arg routeKey "${route_key}" \
      --argjson priority "${priority}" \
      '{template:$template,routeKey:$routeKey,priority:$priority}')
    post_json \
      "${PREPARE_BASE_URL}/api/events/route-templates" \
      "${body}" \
      -H "X-API-Key: ${API_KEY}" >/dev/null
  done
}

create_event_type_mappings() {
  local raw_event_type canonical_event_type body
  for item in "${EVENT_TYPE_MAPPINGS[@]}"; do
    IFS='|' read -r raw_event_type canonical_event_type <<<"${item}"
    body=$(jq -nc \
      --arg rawEventType "${raw_event_type}" \
      --arg canonicalEventType "${canonical_event_type}" \
      '{rawEventType:$rawEventType,canonicalEventType:$canonicalEventType}')
    post_json \
      "${PREPARE_BASE_URL}/api/events/event-type-mappings" \
      "${body}" \
      -H "X-API-Key: ${API_KEY}" >/dev/null
  done
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

seed_via_rds() {
  local sql_file="$1"
  run_psql_file_via_rds "${sql_file}"
}

main() {
  require_command curl
  require_command jq
  require_command date
  require_command docker

  if [[ -z "${ORG_ID}" || -z "${API_KEY}" ]]; then
    echo "ORG_ID and API_KEY are required" >&2
    exit 1
  fi

  if [[ "${IDENTIFIED_EVENTS}" -gt "${TOTAL_EVENTS}" ]]; then
    echo "IDENTIFIED_EVENTS must be <= TOTAL_EVENTS" >&2
    exit 1
  fi

  if [[ "${ANONYMOUS_EVENTS}" -ne $((TOTAL_EVENTS - IDENTIFIED_EVENTS)) ]]; then
    echo "ANONYMOUS_EVENTS must equal TOTAL_EVENTS - IDENTIFIED_EVENTS" >&2
    exit 1
  fi

  trap cleanup_dataset_dir EXIT INT TERM

  mkdir -p "${DATASET_DIR}"
  chmod 700 "${DATASET_DIR}"

  local query_window_to
  query_window_to=$(date -u +"%Y-%m-%dT%H:00:00Z")
  local query_window_from
  query_window_from=$(date -u -d "${query_window_to} -1 day" +"%Y-%m-%dT%H:%M:%SZ")
  local previous_window_from
  previous_window_from=$(date -u -d "${query_window_to} -2 day" +"%Y-%m-%dT%H:%M:%SZ")
  local range_from
  range_from=$(date -u -d "${query_window_to} -7 day" +"%Y-%m-%dT%H:%M:%SZ")
  local range_to="${query_window_to}"

  local seed_started_at
  seed_started_at=$(date --iso-8601=seconds)

  echo "[r1-seed] create route templates"
  create_route_templates

  echo "[r1-seed] create event type mappings"
  create_event_type_mappings

  local hot_paths_sql matched_paths_sql unmatched_paths_sql raw_event_types_sql sql_file
  hot_paths_sql=$(sql_text_array MATCHED_RAW_PATHS)
  matched_paths_sql=$(sql_text_array MATCHED_RAW_PATHS)
  unmatched_paths_sql=$(sql_text_array UNMATCHED_PATHS)
  raw_event_types_sql=$(sql_text_array RAW_EVENT_TYPES)

  sql_file=$(mktemp)
  cat > "${sql_file}" <<SQL
BEGIN;

INSERT INTO users (organization_id, external_user_id, created_at, updated_at)
SELECT
  ${ORG_ID},
  'r1-user-' || lpad(gs::text, 4, '0'),
  TIMESTAMPTZ '${range_from}',
  TIMESTAMPTZ '${range_from}'
FROM generate_series(1, ${IDENTIFIED_USERS}) AS gs;

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
      WHEN gs <= ${IDENTIFIED_EVENTS} THEN
        CASE
          WHEN gs % 100 < 70 THEN 'r1-user-' || lpad((((gs - 1) % 100) + 1)::text, 4, '0')
          ELSE 'r1-user-' || lpad((101 + ((gs - 1) % 900))::text, 4, '0')
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
  FROM generate_series(1, ${TOTAL_EVENTS}) AS gs
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
    ON u.organization_id = ${ORG_ID}
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
  ${ORG_ID},
  occurred_at,
  occurred_at
FROM resolved_events
ORDER BY seq;

COMMIT;
SQL

  echo "[r1-seed] seed users and events"
  seed_via_rds "${sql_file}"
  rm -f "${sql_file}"

  local seed_completed_at
  seed_completed_at=$(date --iso-8601=seconds)

  local route_template_count="${#ROUTE_TEMPLATES[@]}"
  local unmatched_path_count="${#UNMATCHED_PATHS[@]}"
  local raw_path_count=$((route_template_count + unmatched_path_count))
  local mapped_event_type_count="${#EVENT_TYPE_MAPPINGS[@]}"
  local raw_event_type_count="${#RAW_EVENT_TYPES[@]}"
  local unmapped_event_type_count=$((raw_event_type_count - mapped_event_type_count))

  jq -nc \
    --arg datasetVersion "${DATASET_VERSION}" \
    --argjson orgId "${ORG_ID}" \
    --arg orgName "${ORG_NAME}" \
    --arg apiKey "${API_KEY}" \
    --arg apiKeyPrefix "${API_KEY_PREFIX}" \
    --arg rangeFrom "${range_from}" \
    --arg rangeTo "${range_to}" \
    --arg queryWindowFrom "${query_window_from}" \
    --arg queryWindowTo "${query_window_to}" \
    --arg seedStartedAt "${seed_started_at}" \
    --arg seedCompletedAt "${seed_completed_at}" \
    --arg captureTimeRange "${CAPTURE_TIME_RANGE}" \
    --argjson totalEvents "${TOTAL_EVENTS}" \
    --argjson identifiedUsers "${IDENTIFIED_USERS}" \
    --argjson identifiedEvents "${IDENTIFIED_EVENTS}" \
    --argjson anonymousEvents "${ANONYMOUS_EVENTS}" \
    --argjson rawPathCount "${raw_path_count}" \
    --argjson routeTemplateCount "${route_template_count}" \
    --argjson unmatchedPathCount "${unmatched_path_count}" \
    --argjson rawEventTypeCount "${raw_event_type_count}" \
    --argjson mappedEventTypeCount "${mapped_event_type_count}" \
    --argjson unmappedEventTypeCount "${unmapped_event_type_count}" \
    '{
      datasetVersion: $datasetVersion,
      orgId: $orgId,
      orgName: $orgName,
      apiKey: $apiKey,
      apiKeyPrefix: $apiKeyPrefix,
      totalEvents: $totalEvents,
      identifiedUsers: $identifiedUsers,
      identifiedEvents: $identifiedEvents,
      anonymousEvents: $anonymousEvents,
      rawPathCount: $rawPathCount,
      routeTemplateCount: $routeTemplateCount,
      unmatchedPathCount: $unmatchedPathCount,
      rawEventTypeCount: $rawEventTypeCount,
      mappedEventTypeCount: $mappedEventTypeCount,
      unmappedEventTypeCount: $unmappedEventTypeCount,
      rangeFrom: $rangeFrom,
      rangeTo: $rangeTo,
      queryWindowFrom: $queryWindowFrom,
      queryWindowTo: $queryWindowTo,
      captureTimeRange: $captureTimeRange,
      seedStartedAt: $seedStartedAt,
      seedCompletedAt: $seedCompletedAt
    }' > "${DATASET_META_PATH}"

  chmod 600 "${DATASET_META_PATH}"
  DATASET_CREATED="true"
  trap - EXIT INT TERM
  echo "[r1-seed] dataset ready: ${DATASET_META_PATH}"
}

main "$@"
