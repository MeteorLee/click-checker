#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PREPARE_BASE_URL="${PREPARE_BASE_URL:-http://localhost:8080}"
RUN_BASE_URL="${RUN_BASE_URL:-http://app:8080}"
PAIR_RATE="${PAIR_RATE:-10}"
WRITE_RATE="${WRITE_RATE:-${PAIR_RATE}}"
READ_RATE="${READ_RATE:-${PAIR_RATE}}"
RUN_ID="${RUN_ID:-m1-$(date +%Y%m%d-%H%M%S)-w${WRITE_RATE}-r${READ_RATE}}"
OUT_DIR="${OUT_DIR:-artifacts/perf/m1/${RUN_ID}}"
DATASET_VERSION="${DATASET_VERSION:-m1-v1}"
DATASET_DIR="${DATASET_DIR:-artifacts/perf/m1/datasets/${DATASET_VERSION}}"
DATASET_META_PATH="${DATASET_META_PATH:-${DATASET_DIR}/dataset.json}"
DATASET_ORG_NAME="${DATASET_ORG_NAME:-perf-m1-dataset}"
SNAPSHOT_VERSION="${SNAPSHOT_VERSION:-${DATASET_VERSION}-snap1}"
RESET_DATASET="${RESET_DATASET:-false}"
WARMUP="${WARMUP:-2m}"
DURATION="${DURATION:-10m}"
COOLDOWN="${COOLDOWN:-1m}"
WRITE_EVENT_TYPE="${WRITE_EVENT_TYPE:-loadtest_mixed}"
WRITE_PATH_PREFIX="${WRITE_PATH_PREFIX:-/loadtest/m1}"
WRITE_PATH_COUNT="${WRITE_PATH_COUNT:-6}"
WRITE_EXISTING_USER_POOL_SIZE="${WRITE_EXISTING_USER_POOL_SIZE:-1000}"
WRITE_EXISTING_USER_RATIO="${WRITE_EXISTING_USER_RATIO:-80}"
READ_EXTERNAL_USER_ID="${READ_EXTERNAL_USER_ID:-}"
READ_EVENT_TYPE="${READ_EVENT_TYPE:-}"
CAPTURE_MODE="${CAPTURE_MODE:-scripted}"
CAPTURE_PROFILE="${CAPTURE_PROFILE:-m1}"
CAPTURE_TIME_RANGE="${CAPTURE_TIME_RANGE:-start-2m ~ end+2m}"
IS_BASELINE="${IS_BASELINE:-true}"
COMPARE_TO="${COMPARE_TO:-}"
WRITE_P95_THRESHOLD_MS="${WRITE_P95_THRESHOLD_MS:-5000}"
WRITE_P99_THRESHOLD_MS="${WRITE_P99_THRESHOLD_MS:-8000}"
READ_P95_THRESHOLD_MS="${READ_P95_THRESHOLD_MS:-1500}"
READ_P99_THRESHOLD_MS="${READ_P99_THRESHOLD_MS:-3000}"
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

validate_pair_rate() {
  case "${WRITE_RATE}:${READ_RATE}" in
    10:10|20:20|30:30) ;;
    *)
      echo "WRITE_RATE and READ_RATE must be one of: 10:10, 20:20, 30:30" >&2
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

create_dataset_if_needed() {
  if [[ "${RESET_DATASET}" == "true" && -d "${DATASET_DIR}" ]]; then
    rm -rf "${DATASET_DIR}"
  fi

  if [[ -f "${DATASET_META_PATH}" ]]; then
    echo "[m1-prepare] reuse dataset ${DATASET_VERSION}"
    return
  fi

  mkdir -p "${DATASET_DIR}"
  chmod 700 "${DATASET_DIR}"

  local login_id="m1d$(date +%m%d%H%M%S)"
  local password="Perfm1pass123"
  local org_name="${DATASET_ORG_NAME}"

  echo "[m1-prepare] signup ${login_id}"
  local signup_json access_token
  signup_json=$(signup_account "${login_id}" "${password}")
  access_token=$(jq -r '.accessToken // empty' <<<"${signup_json}")

  if [[ -z "${access_token}" ]]; then
    echo "accessToken parse failed" >&2
    exit 1
  fi

  echo "[m1-prepare] create dataset organization ${org_name}"
  local org_json org_id api_key api_key_prefix
  org_json=$(create_organization "${access_token}" "${org_name}")
  org_id=$(jq -r '.organizationId // empty' <<<"${org_json}")
  api_key=$(jq -r '.apiKey // empty' <<<"${org_json}")
  api_key_prefix=$(jq -r '.apiKeyPrefix // empty' <<<"${org_json}")

  if [[ -z "${org_id}" || -z "${api_key}" ]]; then
    echo "organization response parse failed" >&2
    echo "${org_json}" >&2
    exit 1
  fi

  ORG_ID="${org_id}" \
  ORG_NAME="${org_name}" \
  API_KEY="${api_key}" \
  API_KEY_PREFIX="${api_key_prefix}" \
  PREPARE_BASE_URL="${PREPARE_BASE_URL}" \
  DATASET_VERSION="${DATASET_VERSION}" \
  SNAPSHOT_VERSION="${SNAPSHOT_VERSION}" \
  DATASET_DIR="${DATASET_DIR}" \
  DATASET_META_PATH="${DATASET_META_PATH}" \
  CAPTURE_TIME_RANGE="${CAPTURE_TIME_RANGE}" \
  "${SCRIPT_DIR}/seed-dataset-local.sh"
}

restore_snapshot() {
  DATASET_META_PATH="${DATASET_META_PATH}" "${SCRIPT_DIR}/restore-snapshot-local.sh"
}

main() {
  require_command curl
  require_command jq
  require_command date
  validate_pair_rate
  trap cleanup_prepare_out_dir EXIT INT TERM

  if [[ -e "${OUT_DIR}" ]]; then
    echo "OUT_DIR already exists: ${OUT_DIR}" >&2
    exit 1
  fi

  mkdir -p "${OUT_DIR}"
  chmod 700 "${OUT_DIR}"

  create_dataset_if_needed

  if [[ ! -f "${DATASET_META_PATH}" ]]; then
    echo "Dataset metadata not found: ${DATASET_META_PATH}" >&2
    exit 1
  fi

  echo "[m1-prepare] restore snapshot"
  restore_snapshot

  local meta_path="${OUT_DIR}/meta.json"
  jq -nc \
    --arg scenario "M1" \
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
    --arg apiKey "$(jq -r '.apiKey' "${DATASET_META_PATH}")" \
    --arg apiKeyPrefix "$(jq -r '.apiKeyPrefix' "${DATASET_META_PATH}")" \
    --argjson orgId "$(jq -r '.orgId' "${DATASET_META_PATH}")" \
    --arg orgName "$(jq -r '.orgName' "${DATASET_META_PATH}")" \
    --arg datasetVersion "$(jq -r '.datasetVersion' "${DATASET_META_PATH}")" \
    --arg snapshotVersion "$(jq -r '.snapshotVersion' "${DATASET_META_PATH}")" \
    --arg rangeFrom "$(jq -r '.rangeFrom' "${DATASET_META_PATH}")" \
    --arg rangeTo "$(jq -r '.rangeTo' "${DATASET_META_PATH}")" \
    --arg queryWindowFrom "$(jq -r '.queryWindowFrom' "${DATASET_META_PATH}")" \
    --arg queryWindowTo "$(jq -r '.queryWindowTo' "${DATASET_META_PATH}")" \
    --arg seedStartedAt "$(jq -r '.seedStartedAt' "${DATASET_META_PATH}")" \
    --arg seedCompletedAt "$(jq -r '.seedCompletedAt' "${DATASET_META_PATH}")" \
    --arg writePathPrefix "${WRITE_PATH_PREFIX}" \
    --arg writeEventType "${WRITE_EVENT_TYPE}" \
    --arg readEndpoint "/api/v1/events/analytics/aggregates/overview" \
    --arg readExternalUserId "${READ_EXTERNAL_USER_ID}" \
    --arg readEventType "${READ_EVENT_TYPE}" \
    --arg warmup "${WARMUP}" \
    --arg duration "${DURATION}" \
    --arg cooldown "${COOLDOWN}" \
    --argjson totalEvents "$(jq -r '.totalEvents' "${DATASET_META_PATH}")" \
    --argjson identifiedUsers "$(jq -r '.identifiedUsers' "${DATASET_META_PATH}")" \
    --argjson identifiedEvents "$(jq -r '.identifiedEvents' "${DATASET_META_PATH}")" \
    --argjson anonymousEvents "$(jq -r '.anonymousEvents' "${DATASET_META_PATH}")" \
    --argjson rawPathCount "$(jq -r '.rawPathCount' "${DATASET_META_PATH}")" \
    --argjson routeTemplateCount "$(jq -r '.routeTemplateCount' "${DATASET_META_PATH}")" \
    --argjson unmatchedPathCount "$(jq -r '.unmatchedPathCount' "${DATASET_META_PATH}")" \
    --argjson rawEventTypeCount "$(jq -r '.rawEventTypeCount' "${DATASET_META_PATH}")" \
    --argjson mappedEventTypeCount "$(jq -r '.mappedEventTypeCount' "${DATASET_META_PATH}")" \
    --argjson unmappedEventTypeCount "$(jq -r '.unmappedEventTypeCount' "${DATASET_META_PATH}")" \
    --argjson writeRate "${WRITE_RATE}" \
    --argjson readRate "${READ_RATE}" \
    --argjson writePreAllocatedVUs "$(case "${WRITE_RATE}" in 10) echo 20 ;; 20) echo 40 ;; 30) echo 60 ;; esac)" \
    --argjson writeMaxVUs "$(case "${WRITE_RATE}" in 10) echo 60 ;; 20) echo 120 ;; 30) echo 180 ;; esac)" \
    --argjson readPreAllocatedVUs "$(case "${READ_RATE}" in 10) echo 20 ;; 20) echo 40 ;; 30) echo 60 ;; esac)" \
    --argjson readMaxVUs "$(case "${READ_RATE}" in 10) echo 60 ;; 20) echo 120 ;; 30) echo 180 ;; esac)" \
    --argjson existingUserPoolSize "${WRITE_EXISTING_USER_POOL_SIZE}" \
    --argjson existingUserRatio "${WRITE_EXISTING_USER_RATIO}" \
    --argjson pathCount "${WRITE_PATH_COUNT}" \
    --argjson writeP95ThresholdMs "${WRITE_P95_THRESHOLD_MS}" \
    --argjson writeP99ThresholdMs "${WRITE_P99_THRESHOLD_MS}" \
    --argjson readP95ThresholdMs "${READ_P95_THRESHOLD_MS}" \
    --argjson readP99ThresholdMs "${READ_P99_THRESHOLD_MS}" \
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
        datasetVersion: $datasetVersion,
        snapshotVersion: $snapshotVersion,
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
        seedStartedAt: $seedStartedAt,
        seedCompletedAt: $seedCompletedAt
      },
      request: {
        write: {
          pathPrefix: $writePathPrefix,
          eventType: $writeEventType,
          pathCount: $pathCount,
          existingUserPoolSize: $existingUserPoolSize,
          existingUserRatio: $existingUserRatio
        },
        read: {
          endpoint: $readEndpoint,
          from: $queryWindowFrom,
          to: $queryWindowTo,
          externalUserId: (if $readExternalUserId == "" then null else $readExternalUserId end),
          eventType: (if $readEventType == "" then null else $readEventType end)
        }
      },
      load: {
        warmup: $warmup,
        duration: $duration,
        cooldown: $cooldown,
        writeRate: $writeRate,
        readRate: $readRate,
        writePreAllocatedVUs: $writePreAllocatedVUs,
        writeMaxVUs: $writeMaxVUs,
        readPreAllocatedVUs: $readPreAllocatedVUs,
        readMaxVUs: $readMaxVUs,
        writeP95ThresholdMs: $writeP95ThresholdMs,
        writeP99ThresholdMs: $writeP99ThresholdMs,
        readP95ThresholdMs: $readP95ThresholdMs,
        readP99ThresholdMs: $readP99ThresholdMs
      },
      capture: {
        mode: $captureMode,
        profile: $captureProfile,
        timeRange: $captureTimeRange
      },
      artifacts: {
        outDir: $outDir,
        metaPath: ($outDir + "/meta.json"),
        summaryPath: ($outDir + "/summary.json"),
        consolePath: ($outDir + "/console.log")
      }
    }' > "${meta_path}"

  chmod 600 "${meta_path}"
  PREPARE_COMPLETED="true"
  trap - EXIT INT TERM
  echo "[m1-prepare] meta ready: ${meta_path}"
}

main "$@"
