#!/usr/bin/env bash
set -euo pipefail

PREPARE_BASE_URL="${PREPARE_BASE_URL:-http://localhost:8080}"
RUN_BASE_URL="${RUN_BASE_URL:-http://app:8080}"
RATE="${RATE:-50}"
RUN_ID="${RUN_ID:-w1-$(date +%Y%m%d-%H%M%S)-r${RATE}}"
OUT_DIR="${OUT_DIR:-artifacts/perf/local/w1/${RUN_ID}}"
WARMUP="${WARMUP:-2m}"
DURATION="${DURATION:-10m}"
COOLDOWN="${COOLDOWN:-1m}"
EVENT_TYPE="${EVENT_TYPE:-loadtest_write}"
PATH_PREFIX="${PATH_PREFIX:-/loadtest/w1}"
PATH_COUNT="${PATH_COUNT:-6}"
EXISTING_USER_POOL_SIZE="${EXISTING_USER_POOL_SIZE:-1000}"
EXISTING_USER_RATIO="${EXISTING_USER_RATIO:-80}"
SEED_EVENT_TYPE="${SEED_EVENT_TYPE:-loadtest_seed}"
SEED_PATH_PREFIX="${SEED_PATH_PREFIX:-/loadtest/w1/seed}"
SEED_CONCURRENCY="${SEED_CONCURRENCY:-10}"
CAPTURE_MODE="${CAPTURE_MODE:-scripted}"
CAPTURE_PROFILE="${CAPTURE_PROFILE:-w1}"
CAPTURE_TIME_RANGE="${CAPTURE_TIME_RANGE:-start-2m ~ end+2m}"
IS_BASELINE="${IS_BASELINE:-true}"
COMPARE_TO="${COMPARE_TO:-}"
P95_THRESHOLD_MS="${P95_THRESHOLD_MS:-5000}"
P99_THRESHOLD_MS="${P99_THRESHOLD_MS:-8000}"
PREPARE_COMPLETED="false"

cleanup_prepare_out_dir() {
  local exit_code=$?

  if [[ "${PREPARE_COMPLETED}" != "true" && -d "${OUT_DIR}" ]]; then
    rm -rf "${OUT_DIR}"
  fi

  exit "${exit_code}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

validate_rate() {
  case "${RATE}" in
    50|100|200) ;;
    *)
      echo "RATE must be one of: 50, 100, 200" >&2
      exit 1
      ;;
  esac
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

signup_account() {
  local login_id="$1"
  local password="$2"
  local body
  body=$(jq -nc \
    --arg loginId "${login_id}" \
    --arg password "${password}" \
    '{loginId:$loginId,password:$password}')
  post_json "${PREPARE_BASE_URL}/api/v1/admin/auth/signup" "${body}"
}

create_organization() {
  local access_token="$1"
  local org_name="$2"
  local body
  body=$(jq -nc --arg name "${org_name}" '{name:$name}')
  post_json \
    "${PREPARE_BASE_URL}/api/v1/admin/organizations" \
    "${body}" \
    -H "Authorization: Bearer ${access_token}"
}

post_event() {
  local api_key="$1"
  local external_user_id="$2"
  local event_type="$3"
  local path="$4"
  local occurred_at="$5"
  local payload="$6"

  local body
  body=$(jq -nc \
    --arg externalUserId "${external_user_id}" \
    --arg eventType "${event_type}" \
    --arg path "${path}" \
    --arg occurredAt "${occurred_at}" \
    --arg payload "${payload}" \
    '{externalUserId:$externalUserId,eventType:$eventType,path:$path,occurredAt:$occurredAt,payload:$payload}')

  post_json \
    "${PREPARE_BASE_URL}/api/events" \
    "${body}" \
    -H "X-API-Key: ${api_key}" >/dev/null
}

seed_one_user() {
  local api_key="$1"
  local index="$2"
  local occurred_at
  occurred_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  local user_id="existing-${index}"
  local path="${SEED_PATH_PREFIX}/$((index % PATH_COUNT))"
  local payload
  payload=$(jq -nc \
    --arg source "k6-w1-prepare" \
    --arg scenario "W1" \
    --arg phase "seed" \
    --arg runId "${RUN_ID}" \
    --arg externalUserId "${user_id}" \
    '{source:$source,scenario:$scenario,phase:$phase,runId:$runId,externalUserId:$externalUserId}')

  post_event "${api_key}" "${user_id}" "${SEED_EVENT_TYPE}" "${path}" "${occurred_at}" "${payload}"
}

seed_existing_users() {
  local api_key="$1"
  local total="$2"

  local -a pids=()
  local index

  for ((index=0; index<total; index++)); do
    seed_one_user "${api_key}" "${index}" &
    pids+=("$!")

    if ((${#pids[@]} >= SEED_CONCURRENCY)); then
      wait_for_batch "${pids[@]}"
      pids=()
    fi
  done

  if ((${#pids[@]} > 0)); then
    wait_for_batch "${pids[@]}"
  fi
}

wait_for_batch() {
  local pid
  for pid in "$@"; do
    wait "${pid}"
  done
}

main() {
  require_command curl
  require_command jq
  require_command date
  validate_rate
  trap cleanup_prepare_out_dir EXIT INT TERM

  if [[ -e "${OUT_DIR}" ]]; then
    echo "OUT_DIR already exists: ${OUT_DIR}" >&2
    exit 1
  fi

  mkdir -p "${OUT_DIR}"
  chmod 700 "${OUT_DIR}"

  local login_id="w1r${RATE}$(date +%Y%m%d%H%M%S)"
  local password="Perfw1pass123"
  local org_name="perf-w1-r${RATE}-$(date +%Y%m%d%H%M%S)"

  echo "[w1-prepare] signup ${login_id}"
  local signup_json
  signup_json=$(signup_account "${login_id}" "${password}")
  local access_token
  access_token=$(jq -r '.accessToken // empty' <<<"${signup_json}")

  if [[ -z "${access_token}" ]]; then
    echo "accessToken parse failed" >&2
    exit 1
  fi

  echo "[w1-prepare] create organization ${org_name}"
  local org_json
  org_json=$(create_organization "${access_token}" "${org_name}")
  local org_id
  org_id=$(jq -r '.organizationId // empty' <<<"${org_json}")
  local api_key
  api_key=$(jq -r '.apiKey // empty' <<<"${org_json}")
  local api_key_prefix
  api_key_prefix=$(jq -r '.apiKeyPrefix // empty' <<<"${org_json}")

  if [[ -z "${org_id}" || -z "${api_key}" ]]; then
    echo "organization response parse failed" >&2
    echo "${org_json}" >&2
    exit 1
  fi

  local seed_started_at
  seed_started_at=$(date --iso-8601=seconds)
  echo "[w1-prepare] seed ${EXISTING_USER_POOL_SIZE} existing users"
  seed_existing_users "${api_key}" "${EXISTING_USER_POOL_SIZE}"
  local seed_completed_at
  seed_completed_at=$(date --iso-8601=seconds)

  local meta_path="${OUT_DIR}/meta.json"
  jq -nc \
    --arg scenario "W1" \
    --arg runId "${RUN_ID}" \
    --arg timestamp "$(date --iso-8601=seconds)" \
    --arg status "prepared" \
    --argjson isBaseline "$( [[ "${IS_BASELINE}" == "true" ]] && echo true || echo false )" \
    --arg compareTo "${COMPARE_TO}" \
    --arg envName "local" \
    --arg prepareBaseUrl "${PREPARE_BASE_URL}" \
    --arg baseUrl "${RUN_BASE_URL}" \
    --argjson appCount 1 \
    --arg dbInstance "local-postgres" \
    --arg apiKey "${api_key}" \
    --arg apiKeyPrefix "${api_key_prefix}" \
    --argjson orgId "${org_id}" \
    --arg orgName "${org_name}" \
    --arg pathPrefix "${PATH_PREFIX}" \
    --arg eventType "${EVENT_TYPE}" \
    --argjson pathCount "${PATH_COUNT}" \
    --argjson existingUserPoolSize "${EXISTING_USER_POOL_SIZE}" \
    --argjson existingUserRatio "${EXISTING_USER_RATIO}" \
    --arg seedEventType "${SEED_EVENT_TYPE}" \
    --arg seedPathPrefix "${SEED_PATH_PREFIX}" \
    --arg seedStartedAt "${seed_started_at}" \
    --arg seedCompletedAt "${seed_completed_at}" \
    --argjson seedSuccessCount "${EXISTING_USER_POOL_SIZE}" \
    --argjson rate "${RATE}" \
    --arg warmup "${WARMUP}" \
    --arg duration "${DURATION}" \
    --arg cooldown "${COOLDOWN}" \
    --argjson preAllocatedVUs "$(case "${RATE}" in 50) echo 100 ;; 100) echo 200 ;; 200) echo 400 ;; esac)" \
    --argjson maxVUs "$(case "${RATE}" in 50) echo 300 ;; 100) echo 500 ;; 200) echo 800 ;; esac)" \
    --argjson p95ThresholdMs "${P95_THRESHOLD_MS}" \
    --argjson p99ThresholdMs "${P99_THRESHOLD_MS}" \
    --arg captureMode "${CAPTURE_MODE}" \
    --arg captureProfile "${CAPTURE_PROFILE}" \
    --arg captureTimeRange "${CAPTURE_TIME_RANGE}" \
    --arg outDir "${OUT_DIR}" \
    '{
      scenario: $scenario,
      runId: $runId,
      timestamp: $timestamp,
      status: $status,
      isBaseline: $isBaseline,
      compareTo: (if $compareTo == "" then null else $compareTo end),
      env: {
        name: $envName,
        prepareBaseUrl: $prepareBaseUrl,
        baseUrl: $baseUrl,
        appCount: $appCount,
        dbInstance: $dbInstance
      },
      auth: {
        apiKey: $apiKey,
        apiKeyPrefix: $apiKeyPrefix
      },
      dataset: {
        orgId: $orgId,
        orgName: $orgName,
        pathPrefix: $pathPrefix,
        eventType: $eventType,
        pathCount: $pathCount,
        existingUserPoolSize: $existingUserPoolSize,
        existingUserRatio: $existingUserRatio
      },
      seed: {
        eventType: $seedEventType,
        pathPrefix: $seedPathPrefix,
        existingUserPoolSize: $existingUserPoolSize,
        startedAt: $seedStartedAt,
        completedAt: $seedCompletedAt,
        successCount: $seedSuccessCount
      },
      load: {
        rate: $rate,
        warmup: $warmup,
        duration: $duration,
        cooldown: $cooldown,
        preAllocatedVUs: $preAllocatedVUs,
        maxVUs: $maxVUs,
        p95ThresholdMs: $p95ThresholdMs,
        p99ThresholdMs: $p99ThresholdMs
      },
      capture: {
        mode: $captureMode,
        profile: $captureProfile,
        timeRange: $captureTimeRange
      },
      artifacts: {
        outDir: $outDir
      }
    }' > "${meta_path}"
  chmod 600 "${meta_path}"
  PREPARE_COMPLETED="true"

  echo "[w1-prepare] meta=${meta_path}"
  jq -c '{scenario,runId,status,isBaseline,env:{name: .env.name, baseUrl: .env.baseUrl},dataset:{orgId: .dataset.orgId, orgName: .dataset.orgName, existingUserPoolSize: .dataset.existingUserPoolSize},load,capture,auth:{apiKeyPrefix: .auth.apiKeyPrefix}}' "${meta_path}"
}

main "$@"
