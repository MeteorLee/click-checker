#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../../common/v2-dataset-lib.sh"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../../common/v2-dataset-sql.sh"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/db-lib.sh"

PREPARE_BASE_URL="${PREPARE_BASE_URL:-}"
DATASET_VERSION="${DATASET_VERSION:-${V2_DATASET_VERSION_DEFAULT}}"
SNAPSHOT_VERSION="${SNAPSHOT_VERSION:-${V2_SNAPSHOT_VERSION_DEFAULT}}"
DATASET_DIR="${DATASET_DIR:-artifacts/perf/prod-direct/common/datasets/${DATASET_VERSION}}"
DATASET_META_PATH="${DATASET_META_PATH:-${DATASET_DIR}/dataset.json}"
ORG_NAME_PREFIX="${ORG_NAME_PREFIX:-perf-v2-prod-direct}"
RESET_DATASET="${RESET_DATASET:-false}"
DATASET_CREATED="false"

cleanup_v2_prod_dataset_dir() {
  local exit_code=$?
  if [[ "${DATASET_CREATED}" != "true" && -d "${DATASET_DIR}" ]]; then
    rm -rf "${DATASET_DIR}"
  fi
  exit "${exit_code}"
}

apply_v2_sql_rds() {
  local sql_file="$1"
  run_psql_file_via_rds "${sql_file}"
}

query_v2_rds() {
  local sql="$1"
  run_psql_query_via_rds "${sql}" | sed '/^$/d'
}

verify_v2_dataset_rds() {
  local meta_path="$1"
  local org_ids_csv expected_org_count expected_total_events expected_total_users
  local expected_route_templates expected_event_type_mappings expected_pair_count
  local actual_events_by_org actual_users_by_org expected_events_by_org expected_users_by_org
  local funnel_success_orgs retention_day30_orgs

  v2_verify_dataset_meta_contract "${meta_path}"

  org_ids_csv="$(v2_meta_org_ids_csv "${meta_path}")"
  expected_org_count="$(jq -r '.orgs | length' "${meta_path}")"
  expected_total_events="$(jq -r '.totals.totalEvents' "${meta_path}")"
  expected_total_users="$(jq -r '.totals.identifiedUsers' "${meta_path}")"
  expected_route_templates="$(jq -r '(.routeTemplateCount * (.orgs | length))' "${meta_path}")"
  expected_event_type_mappings="$(jq -r '(.eventTypeMappingCount * (.orgs | length))' "${meta_path}")"
  expected_pair_count=$(( expected_org_count * 2 ))

  [[ "$(query_v2_rds "select count(*) from organizations where id in (${org_ids_csv});")" == "${expected_org_count}" ]] || {
    echo "v2 prod-direct verify failed: organization count mismatch" >&2
    exit 1
  }
  [[ "$(query_v2_rds "select count(*) from events where organization_id in (${org_ids_csv});")" == "${expected_total_events}" ]] || {
    echo "v2 prod-direct verify failed: total event count mismatch" >&2
    exit 1
  }
  [[ "$(query_v2_rds "select count(*) from users where organization_id in (${org_ids_csv});")" == "${expected_total_users}" ]] || {
    echo "v2 prod-direct verify failed: total user count mismatch" >&2
    exit 1
  }
  [[ "$(query_v2_rds "select count(*) from route_templates where organization_id in (${org_ids_csv});")" == "${expected_route_templates}" ]] || {
    echo "v2 prod-direct verify failed: route template count mismatch" >&2
    exit 1
  }
  [[ "$(query_v2_rds "select count(*) from event_type_mappings where organization_id in (${org_ids_csv});")" == "${expected_event_type_mappings}" ]] || {
    echo "v2 prod-direct verify failed: event type mapping count mismatch" >&2
    exit 1
  }
  [[ "$(query_v2_rds "select count(*) from route_templates where organization_id in (${org_ids_csv}) and route_key in ('pricing','checkout');")" == "${expected_pair_count}" ]] || {
    echo "v2 prod-direct verify failed: representative route templates missing" >&2
    exit 1
  }
  [[ "$(query_v2_rds "select count(*) from event_type_mappings where organization_id in (${org_ids_csv}) and ((raw_event_type='page_view' and canonical_event_type='view') or (raw_event_type='purchase_complete' and canonical_event_type='purchase'));")" == "${expected_pair_count}" ]] || {
    echo "v2 prod-direct verify failed: representative event mappings missing" >&2
    exit 1
  }

  actual_events_by_org="$(query_v2_rds "select organization_id, count(*) from events where organization_id in (${org_ids_csv}) group by organization_id order by organization_id;")"
  expected_events_by_org="$(v2_meta_expected_events_by_org "${meta_path}")"
  [[ "${actual_events_by_org}" == "${expected_events_by_org}" ]] || {
    echo "v2 prod-direct verify failed: org event distribution mismatch" >&2
    printf 'expected:\n%s\nactual:\n%s\n' "${expected_events_by_org}" "${actual_events_by_org}" >&2
    exit 1
  }

  actual_users_by_org="$(query_v2_rds "select organization_id, count(*) from users where organization_id in (${org_ids_csv}) group by organization_id order by organization_id;")"
  expected_users_by_org="$(v2_meta_expected_users_by_org "${meta_path}")"
  [[ "${actual_users_by_org}" == "${expected_users_by_org}" ]] || {
    echo "v2 prod-direct verify failed: org user distribution mismatch" >&2
    printf 'expected:\n%s\nactual:\n%s\n' "${expected_users_by_org}" "${actual_users_by_org}" >&2
    exit 1
  }

  funnel_success_orgs="$(query_v2_rds "
    with success_users as (
      select distinct e1.organization_id, e1.event_user_id
      from events e1
      join events e2 on e2.organization_id = e1.organization_id and e2.event_user_id = e1.event_user_id
        and e2.occurred_at > e1.occurred_at and e2.occurred_at <= e1.occurred_at + interval '7 day'
      join events e3 on e3.organization_id = e1.organization_id and e3.event_user_id = e1.event_user_id
        and e3.occurred_at > e2.occurred_at and e3.occurred_at <= e1.occurred_at + interval '7 day'
      where e1.organization_id in (${org_ids_csv})
        and e1.event_type = 'page_view' and e1.path = '/pricing'
        and e2.event_type = 'signup_submit' and e2.path = '/signup'
        and e3.event_type = 'purchase_complete' and e3.path = '/checkout'
    )
    select count(distinct organization_id) from success_users;
  ")"
  [[ "${funnel_success_orgs}" == "${expected_org_count}" ]] || {
    echo "v2 prod-direct verify failed: funnel success chain missing" >&2
    exit 1
  }

  retention_day30_orgs="$(query_v2_rds "
    with first_seen as (
      select organization_id, event_user_id, min(occurred_at) as first_seen_at
      from events
      where organization_id in (${org_ids_csv}) and event_user_id is not null
      group by organization_id, event_user_id
    ), revisit as (
      select f.organization_id,
             count(distinct f.event_user_id) filter (where timezone('UTC', e.occurred_at)::date = timezone('UTC', f.first_seen_at)::date + 1) as day1_users,
             count(distinct f.event_user_id) filter (where timezone('UTC', e.occurred_at)::date = timezone('UTC', f.first_seen_at)::date + 7) as day7_users,
             count(distinct f.event_user_id) filter (where timezone('UTC', e.occurred_at)::date = timezone('UTC', f.first_seen_at)::date + 14) as day14_users,
             count(distinct f.event_user_id) filter (where timezone('UTC', e.occurred_at)::date = timezone('UTC', f.first_seen_at)::date + 30) as day30_users
      from first_seen f
      join events e on e.organization_id = f.organization_id and e.event_user_id = f.event_user_id
      group by f.organization_id
    )
    select count(*) from revisit where day1_users > 0 and day7_users > 0 and day14_users > 0 and day30_users > 0;
  ")"
  [[ "${retention_day30_orgs}" == "${expected_org_count}" ]] || {
    echo "v2 prod-direct verify failed: retention revisit pattern missing" >&2
    exit 1
  }

  echo "[v2] post-restore verification passed"
}

main() {
  local orgs_json created_at seed_anchor_at snapshot_sql_path

  v2_require_command curl
  v2_require_command jq
  v2_require_command docker
  v2_require_command date

  if [[ -z "${PREPARE_BASE_URL}" ]]; then
    echo "PREPARE_BASE_URL is required" >&2
    exit 1
  fi

  trap cleanup_v2_prod_dataset_dir EXIT INT TERM

  if [[ "${RESET_DATASET}" == "true" && -d "${DATASET_DIR}" ]]; then
    rm -rf "${DATASET_DIR}"
  fi

  if [[ -f "${DATASET_META_PATH}" ]]; then
    echo "[v2] dataset present -> seed skipped"
    DATASET_CREATED="true"
    return
  fi

  mkdir -p "${DATASET_DIR}"
  chmod 700 "${DATASET_DIR}"

  echo "[v2] dataset missing -> seed"
  orgs_json=$(v2_create_orgs_via_api "${PREPARE_BASE_URL}" "${ORG_NAME_PREFIX}")
  created_at=$(date --iso-8601=seconds)
  seed_anchor_at=$(v2_seed_anchor_utc)

  v2_write_dataset_meta "${DATASET_META_PATH}" "${orgs_json}" "${created_at}" "${seed_anchor_at}"
  snapshot_sql_path=$(v2_snapshot_sql_path_from_meta_path "${DATASET_META_PATH}")
  v2_generate_snapshot_sql "${DATASET_META_PATH}" "${snapshot_sql_path}"
  apply_v2_sql_rds "${snapshot_sql_path}"
  verify_v2_dataset_rds "${DATASET_META_PATH}"
  v2_mark_reset_state_clean "${DATASET_META_PATH}" "seed" "" "full"

  DATASET_CREATED="true"
  echo "[v2-seed-prod-direct] dataset ready: ${DATASET_META_PATH}"
}

main "$@"
