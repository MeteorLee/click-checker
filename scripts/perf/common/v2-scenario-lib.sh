#!/usr/bin/env bash
set -euo pipefail

v2_require_stage() {
  case "${1:-}" in
    1|2|3) ;;
    *)
      echo "STAGE must be one of: 1, 2, 3" >&2
      exit 1
      ;;
  esac
}

v2_require_profile_version() {
  case "${1:-}" in
    1|2|3) ;;
    *)
      echo "PROFILE_VERSION must be one of: 1, 2, 3" >&2
      exit 1
      ;;
  esac
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

v2_stage_label() {
  printf 's%s\n' "$1"
}

v2_profile_label() {
  printf 'v%s\n' "$1"
}

v2_scenario_key() {
  printf '%s\n' "$1" | tr '[:upper:]' '[:lower:]'
}

v2_default_profile_version_for() {
  local scenario="$1"
  local stage="$2"

  case "${scenario}" in
    R4|R5|R6)
      printf '%s\n' "${stage}"
      ;;
    M2)
      printf '2\n'
      ;;
    *)
      echo "PROFILE_VERSION is not used for scenario: ${scenario}" >&2
      exit 1
      ;;
  esac
}

v2_default_reset_mode_for() {
  local scenario="$1"

  case "${scenario}" in
    W2|M2) printf 'quick\n' ;;
    R4|R5|R6) printf 'skip\n' ;;
    *)
      echo "Unsupported scenario/reset mode mapping: ${scenario}" >&2
      exit 1
      ;;
  esac
}

v2_default_preset_for() {
  local scenario="$1"
  local version_ref="$2"

  case "${scenario}:${version_ref}" in
    W2:*) printf 'default\n' ;;
    R4:1|R4:2|R5:1|R5:2|R6:1|R6:2) printf 'default\n' ;;
    R4:3|R5:3|R6:3) printf 'heavy\n' ;;
    M2:1|M2:2) printf 'default\n' ;;
    M2:3) printf 'heavy\n' ;;
    *)
      echo "Unsupported scenario/preset mapping: ${scenario}:${version_ref}" >&2
      exit 1
      ;;
  esac
}

v2_rate_json_for() {
  local scenario="$1"
  local stage="$2"

  case "${scenario}:${stage}" in
    W2:1) printf '{"rate":60}\n' ;;
    W2:2) printf '{"rate":80}\n' ;;
    W2:3) printf '{"rate":100}\n' ;;
    R4:1) printf '{"rate":10}\n' ;;
    R4:2) printf '{"rate":20}\n' ;;
    R4:3) printf '{"rate":30}\n' ;;
    R5:1) printf '{"rate":5}\n' ;;
    R5:2) printf '{"rate":10}\n' ;;
    R5:3) printf '{"rate":15}\n' ;;
    R6:1) printf '{"rate":10}\n' ;;
    R6:2) printf '{"rate":15}\n' ;;
    R6:3) printf '{"rate":20}\n' ;;
    M2:1) printf '{"totalRate":100,"writeRate":95,"readRate":5}\n' ;;
    M2:2) printf '{"totalRate":200,"writeRate":190,"readRate":10}\n' ;;
    M2:3) printf '{"totalRate":300,"writeRate":285,"readRate":15}\n' ;;
    *)
      echo "Unsupported scenario/rate combination: ${scenario}:${stage}" >&2
      exit 1
      ;;
  esac
}

v2_duration_json_for() {
  local env_name="$1"
  local stage="$2"

  case "${env_name}:${stage}" in
    local:1|local:2) printf '{"warmup":"30s","duration":"5m","cooldown":"0"}\n' ;;
    local:3) printf '{"warmup":"30s","duration":"3m","cooldown":"0"}\n' ;;
    prod-direct:1|prod-direct:2) printf '{"warmup":"15s","duration":"1m","cooldown":"0"}\n' ;;
    prod-direct:3) printf '{"warmup":"15s","duration":"30s","cooldown":"0"}\n' ;;
    prod-public:1|prod-public:2) printf '{"warmup":"15s","duration":"30s","cooldown":"0"}\n' ;;
    prod-public:3) printf '{"warmup":"10s","duration":"30s","cooldown":"0"}\n' ;;
    *)
      echo "Unsupported env/stage duration combination: ${env_name}:${stage}" >&2
      exit 1
      ;;
  esac
}

v2_thresholds_json_for() {
  local scenario="$1"

  case "${scenario}" in
    W2)
      printf '{"errorRate":0.01,"p95Ms":5000,"p99Ms":8000}\n'
      ;;
    R4)
      printf '{"errorRate":0.01,"p95Ms":3000,"p99Ms":5000}\n'
      ;;
    R5|R6)
      printf '{"errorRate":0.01,"p95Ms":5000,"p99Ms":8000}\n'
      ;;
    M2)
      printf '{"errorRate":0.01,"writeP95Ms":5000,"writeP99Ms":8000,"readP95Ms":5000,"readP99Ms":8000}\n'
      ;;
    *)
      echo "Unsupported scenario thresholds: ${scenario}" >&2
      exit 1
      ;;
  esac
}

v2_vus_json_for() {
  local scenario="$1"
  local stage="$2"

  case "${scenario}:${stage}" in
    W2:1) printf '{"preAllocatedVUs":120,"maxVUs":300}\n' ;;
    W2:2) printf '{"preAllocatedVUs":160,"maxVUs":400}\n' ;;
    W2:3) printf '{"preAllocatedVUs":200,"maxVUs":500}\n' ;;
    R4:1) printf '{"preAllocatedVUs":20,"maxVUs":60}\n' ;;
    R4:2) printf '{"preAllocatedVUs":40,"maxVUs":120}\n' ;;
    R4:3) printf '{"preAllocatedVUs":60,"maxVUs":180}\n' ;;
    R5:1) printf '{"preAllocatedVUs":20,"maxVUs":60}\n' ;;
    R5:2) printf '{"preAllocatedVUs":40,"maxVUs":120}\n' ;;
    R5:3) printf '{"preAllocatedVUs":60,"maxVUs":180}\n' ;;
    R6:1) printf '{"preAllocatedVUs":30,"maxVUs":90}\n' ;;
    R6:2) printf '{"preAllocatedVUs":45,"maxVUs":135}\n' ;;
    R6:3) printf '{"preAllocatedVUs":60,"maxVUs":180}\n' ;;
    M2:1) printf '{"writePreAllocatedVUs":200,"writeMaxVUs":500,"readPreAllocatedVUs":20,"readMaxVUs":60}\n' ;;
    M2:2) printf '{"writePreAllocatedVUs":400,"writeMaxVUs":800,"readPreAllocatedVUs":40,"readMaxVUs":120}\n' ;;
    M2:3) printf '{"writePreAllocatedVUs":600,"writeMaxVUs":1200,"readPreAllocatedVUs":60,"readMaxVUs":180}\n' ;;
    *)
      echo "Unsupported scenario/VU combination: ${scenario}:${stage}" >&2
      exit 1
      ;;
  esac
}

v2_shift_anchor() {
  local anchor="$1"
  local shift_expr="$2"
  date -u -d "${anchor} ${shift_expr}" +"%Y-%m-%dT%H:%M:%SZ"
}

v2_profiles_init() {
  printf '[]\n'
}

v2_profile_append_get() {
  local current_json="$1"
  local name="$2"
  local path="$3"
  local weight="$4"
  local expect="$5"
  local query_json="$6"

  jq -nc \
    --argjson current "${current_json}" \
    --arg name "${name}" \
    --arg method "GET" \
    --arg path "${path}" \
    --argjson weight "${weight}" \
    --arg expect "${expect}" \
    --argjson query "${query_json}" \
    '$current + [{
      name: $name,
      method: $method,
      path: $path,
      weight: $weight,
      expect: $expect,
      query: $query
    }]'
}

v2_profile_append_post() {
  local current_json="$1"
  local name="$2"
  local path="$3"
  local weight="$4"
  local expect="$5"
  local body_json="$6"

  jq -nc \
    --argjson current "${current_json}" \
    --arg name "${name}" \
    --arg method "POST" \
    --arg path "${path}" \
    --argjson weight "${weight}" \
    --arg expect "${expect}" \
    --argjson body "${body_json}" \
    '$current + [{
      name: $name,
      method: $method,
      path: $path,
      weight: $weight,
      expect: $expect,
      body: $body
    }]'
}

v2_query_json() {
  local from="$1"
  local to="$2"
  shift 2

  local query_json
  query_json=$(jq -nc --arg from "${from}" --arg to "${to}" '{from:$from,to:$to}')

  while (($# > 0)); do
    local key="$1"
    local value="$2"
    shift 2
    query_json=$(jq -nc \
      --argjson current "${query_json}" \
      --arg key "${key}" \
      --arg value "${value}" \
      '$current + {($key): $value}')
  done

  printf '%s\n' "${query_json}"
}

v2_query_json_with_number() {
  local current_json="$1"
  local key="$2"
  local number="$3"
  jq -nc --argjson current "${current_json}" --arg key "${key}" --argjson value "${number}" '$current + {($key): $value}'
}

v2_funnel_body_json() {
  local from="$1"
  local to="$2"
  jq -nc \
    --arg from "${from}" \
    --arg to "${to}" \
    '{
      from: $from,
      to: $to,
      conversionWindowDays: 7,
      steps: [
        {canonicalEventType: "view", routeKey: "pricing"},
        {canonicalEventType: "signup", routeKey: null},
        {canonicalEventType: "purchase", routeKey: null}
      ]
    }'
}

v2_r4_window_json() {
  local anchor="$1"
  local preset="$2"
  local from

  if [[ "${preset}" == "default" ]]; then
    from=$(v2_shift_anchor "${anchor}" "-24 hours")
  else
    from=$(v2_shift_anchor "${anchor}" "-7 days")
  fi

  jq -nc --arg from "${from}" --arg to "${anchor}" '{from:$from,to:$to}'
}

v2_r5_window_json() {
  local anchor="$1"
  local preset="$2"
  local users_from retention_from

  if [[ "${preset}" == "default" ]]; then
    users_from=$(v2_shift_anchor "${anchor}" "-7 days")
    retention_from=$(v2_shift_anchor "${anchor}" "-30 days")
  else
    users_from=$(v2_shift_anchor "${anchor}" "-30 days")
    retention_from=$(v2_shift_anchor "${anchor}" "-90 days")
  fi

  jq -nc \
    --arg usersFrom "${users_from}" \
    --arg retentionFrom "${retention_from}" \
    --arg to "${anchor}" \
    '{usersFrom:$usersFrom,retentionFrom:$retentionFrom,to:$to}'
}

v2_r4_profiles_json() {
  local anchor="$1"
  local preset="$2"
  local profile_version="$3"
  local from to bucket profiles overview_weight routes_weight event_types_weight time_buckets_weight

  from=$(jq -r '.from' <<<"$(v2_r4_window_json "${anchor}" "${preset}")")
  to=$(jq -r '.to' <<<"$(v2_r4_window_json "${anchor}" "${preset}")")
  bucket=$([[ "${preset}" == "default" ]] && printf 'HOUR\n' || printf 'DAY\n')
  profiles=$(v2_profiles_init)

  case "${profile_version}" in
    1)
      profiles=$(v2_profile_append_get "${profiles}" "overview" "/api/v1/events/analytics/aggregates/overview" 40 "overview" "$(v2_query_json "${from}" "${to}")")
      profiles=$(v2_profile_append_get "${profiles}" "routes" "/api/v1/events/analytics/aggregates/routes" 30 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "event-types" "/api/v1/events/analytics/aggregates/event-types" 20 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "time-buckets" "/api/v1/events/analytics/aggregates/time-buckets" 10 "items" "$(v2_query_json "${from}" "${to}" "bucket" "${bucket}" "timezone" "UTC")")
      ;;
    2)
      profiles=$(v2_profile_append_get "${profiles}" "overview" "/api/v1/events/analytics/aggregates/overview" 25 "overview" "$(v2_query_json "${from}" "${to}")")
      profiles=$(v2_profile_append_get "${profiles}" "routes" "/api/v1/events/analytics/aggregates/routes" 20 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "event-types" "/api/v1/events/analytics/aggregates/event-types" 15 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "time-buckets" "/api/v1/events/analytics/aggregates/time-buckets" 10 "items" "$(v2_query_json "${from}" "${to}" "bucket" "${bucket}" "timezone" "UTC")")
      profiles=$(v2_profile_append_get "${profiles}" "route-event-types" "/api/v1/events/analytics/aggregates/route-event-types" 10 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "route-time-buckets" "/api/v1/events/analytics/aggregates/route-time-buckets" 5 "items" "$(v2_query_json "${from}" "${to}" "bucket" "${bucket}" "timezone" "UTC")")
      profiles=$(v2_profile_append_get "${profiles}" "event-type-time-buckets" "/api/v1/events/analytics/aggregates/event-type-time-buckets" 5 "items" "$(v2_query_json "${from}" "${to}" "bucket" "${bucket}" "timezone" "UTC")")
      profiles=$(v2_profile_append_get "${profiles}" "route-event-type-time-buckets" "/api/v1/events/analytics/aggregates/route-event-type-time-buckets" 5 "items" "$(v2_query_json "${from}" "${to}" "bucket" "${bucket}" "timezone" "UTC")")
      profiles=$(v2_profile_append_get "${profiles}" "routes-unique-users" "/api/v1/events/analytics/aggregates/routes/unique-users" 3 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "event-types-unique-users" "/api/v1/events/analytics/aggregates/event-types/unique-users" 2 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      ;;
    3)
      profiles=$(v2_profile_append_get "${profiles}" "overview" "/api/v1/events/analytics/aggregates/overview" 18 "overview" "$(v2_query_json "${from}" "${to}")")
      profiles=$(v2_profile_append_get "${profiles}" "routes" "/api/v1/events/analytics/aggregates/routes" 14 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "event-types" "/api/v1/events/analytics/aggregates/event-types" 11 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "time-buckets" "/api/v1/events/analytics/aggregates/time-buckets" 7 "items" "$(v2_query_json "${from}" "${to}" "bucket" "${bucket}" "timezone" "UTC")")
      profiles=$(v2_profile_append_get "${profiles}" "route-event-types" "/api/v1/events/analytics/aggregates/route-event-types" 16 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "route-time-buckets" "/api/v1/events/analytics/aggregates/route-time-buckets" 8 "items" "$(v2_query_json "${from}" "${to}" "bucket" "${bucket}" "timezone" "UTC")")
      profiles=$(v2_profile_append_get "${profiles}" "event-type-time-buckets" "/api/v1/events/analytics/aggregates/event-type-time-buckets" 8 "items" "$(v2_query_json "${from}" "${to}" "bucket" "${bucket}" "timezone" "UTC")")
      profiles=$(v2_profile_append_get "${profiles}" "route-event-type-time-buckets" "/api/v1/events/analytics/aggregates/route-event-type-time-buckets" 8 "items" "$(v2_query_json "${from}" "${to}" "bucket" "${bucket}" "timezone" "UTC")")
      profiles=$(v2_profile_append_get "${profiles}" "routes-unique-users" "/api/v1/events/analytics/aggregates/routes/unique-users" 5 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      profiles=$(v2_profile_append_get "${profiles}" "event-types-unique-users" "/api/v1/events/analytics/aggregates/event-types/unique-users" 5 "items" "$(v2_query_json_with_number "$(v2_query_json "${from}" "${to}")" "top" 10)")
      ;;
    *)
      echo "Unsupported R4 profile version: ${profile_version}" >&2
      exit 1
      ;;
  esac

  printf '%s\n' "${profiles}"
}

v2_r5_profiles_json() {
  local anchor="$1"
  local preset="$2"
  local profile_version="$3"
  local windows_json users_from retention_from to profiles

  windows_json="$(v2_r5_window_json "${anchor}" "${preset}")"
  users_from=$(jq -r '.usersFrom' <<<"${windows_json}")
  retention_from=$(jq -r '.retentionFrom' <<<"${windows_json}")
  to=$(jq -r '.to' <<<"${windows_json}")
  profiles=$(v2_profiles_init)

  case "${profile_version}" in
    1)
      profiles=$(v2_profile_append_get "${profiles}" "users-overview" "/api/v1/events/analytics/users/overview" 25 "usersOverview" "$(v2_query_json "${users_from}" "${to}")")
      profiles=$(v2_profile_append_post "${profiles}" "funnels-report" "/api/v1/events/analytics/funnels/report" 35 "funnelReport" "$(v2_funnel_body_json "${users_from}" "${to}")")
      profiles=$(v2_profile_append_get "${profiles}" "retention-daily" "/api/v1/events/analytics/retention/daily" 40 "dailyRetention" "$(v2_query_json "${retention_from}" "${to}" "timezone" "UTC" "minCohortUsers" "1")")
      ;;
    2)
      profiles=$(v2_profile_append_get "${profiles}" "users-overview" "/api/v1/events/analytics/users/overview" 20 "usersOverview" "$(v2_query_json "${users_from}" "${to}")")
      profiles=$(v2_profile_append_post "${profiles}" "funnels-report" "/api/v1/events/analytics/funnels/report" 30 "funnelReport" "$(v2_funnel_body_json "${users_from}" "${to}")")
      profiles=$(v2_profile_append_get "${profiles}" "retention-daily" "/api/v1/events/analytics/retention/daily" 30 "dailyRetention" "$(v2_query_json "${retention_from}" "${to}" "timezone" "UTC" "minCohortUsers" "1")")
      profiles=$(v2_profile_append_get "${profiles}" "retention-matrix" "/api/v1/events/analytics/retention/matrix" 20 "retentionMatrix" "$(v2_query_json "${retention_from}" "${to}" "timezone" "UTC" "minCohortUsers" "1" "days" "1,7,14,30")")
      ;;
    3)
      profiles=$(v2_profile_append_get "${profiles}" "users-overview" "/api/v1/events/analytics/users/overview" 10 "usersOverview" "$(v2_query_json "${users_from}" "${to}")")
      profiles=$(v2_profile_append_post "${profiles}" "funnels-report" "/api/v1/events/analytics/funnels/report" 30 "funnelReport" "$(v2_funnel_body_json "${users_from}" "${to}")")
      profiles=$(v2_profile_append_get "${profiles}" "retention-daily" "/api/v1/events/analytics/retention/daily" 20 "dailyRetention" "$(v2_query_json "${retention_from}" "${to}" "timezone" "UTC" "minCohortUsers" "1")")
      profiles=$(v2_profile_append_get "${profiles}" "retention-matrix" "/api/v1/events/analytics/retention/matrix" 40 "retentionMatrix" "$(v2_query_json "${retention_from}" "${to}" "timezone" "UTC" "minCohortUsers" "1" "days" "1,7,14,30")")
      ;;
    *)
      echo "Unsupported R5 profile version: ${profile_version}" >&2
      exit 1
      ;;
  esac

  printf '%s\n' "${profiles}"
}

v2_r6_request_json() {
  local anchor="$1"
  local preset="$2"
  local profile_version="$3"
  local r4_profiles r5_profiles

  r4_profiles="$(v2_r4_profiles_json "${anchor}" "${preset}" "${profile_version}")"
  r5_profiles="$(v2_r5_profiles_json "${anchor}" "${preset}" "${profile_version}")"

  jq -nc \
    --arg preset "${preset}" \
    --arg profileLabel "$(v2_profile_label "${profile_version}")" \
    --argjson profileVersion "${profile_version}" \
    --argjson familyWeights '{"r4":70,"r5":30}' \
    --argjson r4Profiles "${r4_profiles}" \
    --argjson r5Profiles "${r5_profiles}" \
    '{
      preset: $preset,
      profileVersion: $profileVersion,
      profileLabel: $profileLabel,
      familyWeights: $familyWeights,
      r4Profiles: $r4Profiles,
      r5Profiles: $r5Profiles
    }'
}

v2_w2_request_json() {
  local dataset_meta_path="$1"
  jq -c '{
    payloadDistributions: .distributions,
    funnelRules: .funnelRules,
    retentionRules: .retentionRules
  }' "${dataset_meta_path}"
}
