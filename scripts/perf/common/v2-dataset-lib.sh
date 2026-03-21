#!/usr/bin/env bash
set -euo pipefail

V2_DATASET_VERSION_DEFAULT="${V2_DATASET_VERSION_DEFAULT:-v2-dataset-v1}"
V2_SNAPSHOT_VERSION_DEFAULT="${V2_SNAPSHOT_VERSION_DEFAULT:-${V2_DATASET_VERSION_DEFAULT}-snap1}"
V2_TOTAL_EVENTS_DEFAULT="${V2_TOTAL_EVENTS_DEFAULT:-240000}"
V2_IDENTIFIED_USERS_DEFAULT="${V2_IDENTIFIED_USERS_DEFAULT:-7200}"
V2_SCHEMA_FINGERPRINT="${V2_SCHEMA_FINGERPRINT:-unknown}"
V2_APP_COMMIT="${V2_APP_COMMIT:-unknown}"

V2_ORG_KEYS=("hot-1" "warm-1" "warm-2" "cold-1" "cold-2" "cold-3")
V2_ORG_TIERS=("hot" "warm" "warm" "cold" "cold" "cold")
V2_ORG_SHARE_PCTS=("50" "15" "15" "6.6667" "6.6667" "6.6667")
V2_ORG_EVENT_COUNTS=(120000 36000 36000 16000 16000 16000)
V2_ORG_IDENTIFIED_USERS=(3600 1080 1080 480 480 480)

V2_ROUTE_TEMPLATES=(
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

V2_EVENT_TYPE_MAPPINGS=(
  "page_view|view"
  "button_click|click"
  "signup_submit|signup"
  "purchase_complete|purchase"
)

V2_BROWSE_PATHS=(
  "/home"
  "/docs"
  "/blog"
  "/posts/10"
  "/posts/10/comments"
  "/products/8"
)

V2_PRODUCT_PATHS=(
  "/dashboard"
  "/projects/1"
  "/projects/1/overview"
  "/projects/1/routes"
  "/teams/1"
  "/teams/1/projects"
  "/products/8/reviews"
)

V2_CONVERSION_PATHS=(
  "/pricing"
  "/signup"
  "/checkout"
)

V2_RAW_EVENT_TYPES=(
  "page_view"
  "button_click"
  "signup_submit"
  "purchase_complete"
)

v2_require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

v2_require_reset_mode() {
  case "${1:-}" in
    full|quick|skip) ;;
    *)
      echo "RESET_MODE must be one of: full, quick, skip" >&2
      exit 1
      ;;
  esac
}

v2_seed_anchor_utc() {
  date -u +"%Y-%m-%dT%H:00:00Z"
}

v2_snapshot_sql_path_from_meta_path() {
  local meta_path="$1"
  printf '%s/snapshot.sql\n' "$(dirname "${meta_path}")"
}

v2_reset_state_path_from_meta_path() {
  local meta_path="$1"
  printf '%s/reset-state.json\n' "$(dirname "${meta_path}")"
}

v2_build_org_plan_json() {
  local org_name_prefix="$1"
  local orgs_json='[]'
  local index key tier share_pct total_events identified_users org_name

  for index in "${!V2_ORG_KEYS[@]}"; do
    key="${V2_ORG_KEYS[$index]}"
    tier="${V2_ORG_TIERS[$index]}"
    share_pct="${V2_ORG_SHARE_PCTS[$index]}"
    total_events="${V2_ORG_EVENT_COUNTS[$index]}"
    identified_users="${V2_ORG_IDENTIFIED_USERS[$index]}"
    org_name="${org_name_prefix}-${key}"

    orgs_json=$(
      jq -nc \
        --argjson current "${orgs_json}" \
        --arg key "${key}" \
        --arg tier "${tier}" \
        --arg orgName "${org_name}" \
        --argjson sharePct "${share_pct}" \
        --argjson totalEvents "${total_events}" \
        --argjson identifiedUsers "${identified_users}" \
        '$current + [{
          key: $key,
          tier: $tier,
          orgName: $orgName,
          sharePct: $sharePct,
          totalEvents: $totalEvents,
          identifiedUsers: $identifiedUsers
        }]'
    )
  done

  printf '%s\n' "${orgs_json}"
}

v2_post_json() {
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

v2_signup_account() {
  local prepare_base_url="$1"
  local login_id="$2"
  local password="$3"
  local body

  body=$(jq -nc \
    --arg loginId "${login_id}" \
    --arg password "${password}" \
    '{loginId:$loginId,password:$password}')

  v2_post_json "${prepare_base_url}/api/v1/admin/auth/signup" "${body}"
}

v2_create_organization() {
  local prepare_base_url="$1"
  local access_token="$2"
  local org_name="$3"
  local body

  body=$(jq -nc --arg name "${org_name}" '{name:$name}')
  v2_post_json \
    "${prepare_base_url}/api/v1/admin/organizations" \
    "${body}" \
    -H "Authorization: Bearer ${access_token}"
}

v2_create_orgs_via_api() {
  local prepare_base_url="$1"
  local org_name_prefix="$2"
  local login_id="v2d$(date +%m%d%H%M%S)"
  local password="Perfv2pass123"
  local signup_json access_token
  local orgs_json='[]'
  local index key tier share_pct total_events identified_users org_name
  local org_response org_id api_key api_key_prefix

  signup_json=$(v2_signup_account "${prepare_base_url}" "${login_id}" "${password}")
  access_token=$(jq -r '.accessToken // empty' <<<"${signup_json}")

  if [[ -z "${access_token}" ]]; then
    echo "accessToken parse failed for v2 dataset signup" >&2
    exit 1
  fi

  for index in "${!V2_ORG_KEYS[@]}"; do
    key="${V2_ORG_KEYS[$index]}"
    tier="${V2_ORG_TIERS[$index]}"
    share_pct="${V2_ORG_SHARE_PCTS[$index]}"
    total_events="${V2_ORG_EVENT_COUNTS[$index]}"
    identified_users="${V2_ORG_IDENTIFIED_USERS[$index]}"
    org_name="${org_name_prefix}-${key}"

    org_response=$(v2_create_organization "${prepare_base_url}" "${access_token}" "${org_name}")
    org_id=$(jq -r '.organizationId // empty' <<<"${org_response}")
    api_key=$(jq -r '.apiKey // empty' <<<"${org_response}")
    api_key_prefix=$(jq -r '.apiKeyPrefix // empty' <<<"${org_response}")

    if [[ -z "${org_id}" || -z "${api_key}" ]]; then
      echo "organization response parse failed for ${org_name}" >&2
      echo "${org_response}" >&2
      exit 1
    fi

    orgs_json=$(
      jq -nc \
        --argjson current "${orgs_json}" \
        --arg key "${key}" \
        --arg tier "${tier}" \
        --arg orgName "${org_name}" \
        --arg apiKey "${api_key}" \
        --arg apiKeyPrefix "${api_key_prefix}" \
        --argjson orgId "${org_id}" \
        --argjson sharePct "${share_pct}" \
        --argjson totalEvents "${total_events}" \
        --argjson identifiedUsers "${identified_users}" \
        '$current + [{
          key: $key,
          tier: $tier,
          orgName: $orgName,
          orgId: $orgId,
          apiKey: $apiKey,
          apiKeyPrefix: $apiKeyPrefix,
          sharePct: $sharePct,
          totalEvents: $totalEvents,
          identifiedUsers: $identifiedUsers
        }]'
    )
  done

  printf '%s\n' "${orgs_json}"
}

v2_write_dataset_meta() {
  local meta_path="$1"
  local orgs_json="$2"
  local created_at="$3"
  local seed_anchor_at="$4"
  local snapshot_sql_path

  snapshot_sql_path="$(v2_snapshot_sql_path_from_meta_path "${meta_path}")"

  jq -nc \
    --arg datasetVersion "${DATASET_VERSION:-${V2_DATASET_VERSION_DEFAULT}}" \
    --arg snapshotVersion "${SNAPSHOT_VERSION:-${V2_SNAPSHOT_VERSION_DEFAULT}}" \
    --arg createdAt "${created_at}" \
    --arg seedAnchorAt "${seed_anchor_at}" \
    --arg snapshotSqlPath "${snapshot_sql_path}" \
    --arg schemaFingerprint "${V2_SCHEMA_FINGERPRINT}" \
    --arg appCommit "${V2_APP_COMMIT}" \
    --argjson totalEvents "${V2_TOTAL_EVENTS_DEFAULT}" \
    --argjson identifiedUsers "${V2_IDENTIFIED_USERS_DEFAULT}" \
    --argjson orgs "${orgs_json}" \
    '{
      datasetVersion: $datasetVersion,
      snapshotVersion: $snapshotVersion,
      createdAt: $createdAt,
      seedAnchorAt: $seedAnchorAt,
      snapshotSqlPath: $snapshotSqlPath,
      schemaFingerprint: $schemaFingerprint,
      appCommit: $appCommit,
      totals: {
        totalEvents: $totalEvents,
        identifiedUsers: $identifiedUsers
      },
      distributions: {
        identifiedVsAnonymous: {identified: 70, anonymous: 30},
        identifiedExistingVsNew: {existing: 80, new: 20},
        eventTypes: {view: 50, click: 30, signup: 12, purchase: 8},
        pathGroups: {browse: 60, product: 25, conversion: 15},
        dayRanges: {"last7Days": 50, "days8To30": 30, "days31To90": 20},
        hourRanges: {"09to18": 60, "18to24": 25, "00to09": 15}
      },
      funnelRules: {
        successChain: [
          "view + routeKey=pricing",
          "signup",
          "purchase"
        ],
        failureChains: [
          "view + routeKey=pricing -> signup -> drop",
          "view -> click -> drop"
        ],
        conversionWindowDays: 7
      },
      retentionRules: {
        revisitDays: [1, 7, 14, 30]
      },
      routeTemplateCount: 24,
      eventTypeMappingCount: 4,
      orgs: $orgs
    }' > "${meta_path}"
}

v2_load_dataset_meta_value() {
  local meta_path="$1"
  local jq_filter="$2"
  jq -r "${jq_filter}" "${meta_path}"
}

v2_write_reset_state_json() {
  local meta_path="$1"
  local dirty="$2"
  local source="$3"
  local scenario="${4:-}"
  local run_id="${5:-}"
  local status="${6:-}"
  local requested_reset_mode="${7:-}"
  local effective_reset_mode="${8:-}"
  local reset_state_path
  reset_state_path="$(v2_reset_state_path_from_meta_path "${meta_path}")"

  jq -nc \
    --arg datasetVersion "$(v2_load_dataset_meta_value "${meta_path}" '.datasetVersion')" \
    --arg snapshotVersion "$(v2_load_dataset_meta_value "${meta_path}" '.snapshotVersion')" \
    --arg updatedAt "$(date --iso-8601=seconds)" \
    --arg source "${source}" \
    --arg scenario "${scenario}" \
    --arg runId "${run_id}" \
    --arg status "${status}" \
    --arg requestedResetMode "${requested_reset_mode}" \
    --arg effectiveResetMode "${effective_reset_mode}" \
    --argjson dirty "$( [[ "${dirty}" == "true" ]] && printf true || printf false )" \
    '{
      datasetVersion: $datasetVersion,
      snapshotVersion: $snapshotVersion,
      updatedAt: $updatedAt,
      dirty: $dirty,
      source: $source,
      scenario: (if $scenario == "" then null else $scenario end),
      runId: (if $runId == "" then null else $runId end),
      status: (if $status == "" then null else $status end),
      requestedResetMode: (if $requestedResetMode == "" then null else $requestedResetMode end),
      effectiveResetMode: (if $effectiveResetMode == "" then null else $effectiveResetMode end)
    }' > "${reset_state_path}"
}

v2_mark_reset_state_clean() {
  local meta_path="$1"
  local source="$2"
  local requested_reset_mode="${3:-}"
  local effective_reset_mode="${4:-}"
  v2_write_reset_state_json "${meta_path}" "false" "${source}" "" "" "" "${requested_reset_mode}" "${effective_reset_mode}"
}

v2_mark_reset_state_dirty() {
  local meta_path="$1"
  local source="$2"
  local scenario="$3"
  local run_id="$4"
  local status="$5"
  v2_write_reset_state_json "${meta_path}" "true" "${source}" "${scenario}" "${run_id}" "${status}"
}

v2_load_reset_state_value() {
  local meta_path="$1"
  local jq_filter="$2"
  local reset_state_path
  reset_state_path="$(v2_reset_state_path_from_meta_path "${meta_path}")"

  if [[ ! -f "${reset_state_path}" ]]; then
    return 1
  fi

  jq -r "${jq_filter}" "${reset_state_path}"
}

v2_effective_reset_mode() {
  local default_mode="$1"
  local meta_path="$2"
  local reset_state_path dirty
  reset_state_path="$(v2_reset_state_path_from_meta_path "${meta_path}")"

  if [[ ! -f "${reset_state_path}" ]]; then
    printf '%s\n' "${default_mode}"
    return
  fi

  dirty="$(jq -r '.dirty // false' "${reset_state_path}")"
  if [[ "${default_mode}" == "skip" && "${dirty}" == "true" ]]; then
    printf 'quick\n'
    return
  fi

  printf '%s\n' "${default_mode}"
}

v2_meta_org_ids_csv() {
  local meta_path="$1"
  jq -r '[.orgs[].orgId | tostring] | join(",")' "${meta_path}"
}

v2_meta_expected_events_by_org() {
  local meta_path="$1"
  jq -r '.orgs | sort_by(.orgId)[] | "\(.orgId)|\(.totalEvents)"' "${meta_path}"
}

v2_meta_expected_users_by_org() {
  local meta_path="$1"
  jq -r '.orgs | sort_by(.orgId)[] | "\(.orgId)|\(.identifiedUsers)"' "${meta_path}"
}

v2_verify_dataset_meta_contract() {
  local meta_path="$1"
  local expected_org_count="${#V2_ORG_KEYS[@]}"

  [[ -f "${meta_path}" ]] || {
    echo "Dataset metadata not found: ${meta_path}" >&2
    exit 1
  }

  jq -e \
    --arg version "${DATASET_VERSION:-${V2_DATASET_VERSION_DEFAULT}}" \
    --arg snapshotVersion "${SNAPSHOT_VERSION:-${V2_SNAPSHOT_VERSION_DEFAULT}}" \
    --argjson expectedOrgCount "${expected_org_count}" \
    --argjson expectedTotalEvents "${V2_TOTAL_EVENTS_DEFAULT}" \
    --argjson expectedIdentifiedUsers "${V2_IDENTIFIED_USERS_DEFAULT}" \
    '
    .datasetVersion == $version and
    .snapshotVersion == $snapshotVersion and
    (.orgs | length) == $expectedOrgCount and
    .totals.totalEvents == $expectedTotalEvents and
    .totals.identifiedUsers == $expectedIdentifiedUsers and
    .routeTemplateCount == 24 and
    .eventTypeMappingCount == 4 and
    (.snapshotSqlPath | type) == "string" and
    ([.orgs[].orgId] | all(. != null)) and
    ([.orgs[].apiKey] | all((type == "string") and (length > 0))) and
    ([.orgs[].apiKeyPrefix] | all((type == "string") and (length > 0))) and
    (([.orgs[].totalEvents] | add) == $expectedTotalEvents) and
    (([.orgs[].identifiedUsers] | add) == $expectedIdentifiedUsers) and
    (((( [.orgs[].sharePct] | add ) - 100) | if . < 0 then -. else . end) < 0.1)
    ' "${meta_path}" >/dev/null
}
