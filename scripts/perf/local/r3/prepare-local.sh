#!/usr/bin/env bash
set -euo pipefail

PREPARE_BASE_URL="${PREPARE_BASE_URL:-http://localhost:8080}"
RUN_BASE_URL="${RUN_BASE_URL:-http://app:8080}"
RATE="${RATE:-10}"
RUN_ID="${RUN_ID:-r3-$(date +%Y%m%d-%H%M%S)-r${RATE}}"
OUT_DIR="${OUT_DIR:-artifacts/perf/local/r3/${RUN_ID}}"
DATASET_VERSION="${DATASET_VERSION:-r1-v1}"
DATASET_DIR="${DATASET_DIR:-artifacts/perf/local/r1/datasets/${DATASET_VERSION}}"
DATASET_META_PATH="${DATASET_META_PATH:-${DATASET_DIR}/dataset.json}"
QUERY_PRESET="${QUERY_PRESET:-r3-time-buckets-7d-hour-utc}"
BUCKET="${BUCKET:-HOUR}"
TIMEZONE_NAME="${TIMEZONE_NAME:-UTC}"
EXTERNAL_USER_ID="${EXTERNAL_USER_ID:-}"
EVENT_TYPE_FILTER="${EVENT_TYPE_FILTER:-}"
WARMUP="${WARMUP:-2m}"
DURATION="${DURATION:-10m}"
COOLDOWN="${COOLDOWN:-1m}"
CAPTURE_MODE="${CAPTURE_MODE:-scripted}"
CAPTURE_PROFILE="${CAPTURE_PROFILE:-r1}"
CAPTURE_TIME_RANGE="${CAPTURE_TIME_RANGE:-start-2m ~ end+2m}"
IS_BASELINE="${IS_BASELINE:-true}"
COMPARE_TO="${COMPARE_TO:-}"
P95_THRESHOLD_MS="${P95_THRESHOLD_MS:-1500}"
P99_THRESHOLD_MS="${P99_THRESHOLD_MS:-3000}"
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
    10|30|50) ;;
    *)
      echo "RATE must be one of: 10, 30, 50" >&2
      exit 1
      ;;
  esac
}

validate_bucket() {
  case "${BUCKET}" in
    HOUR|DAY) ;;
    *)
      echo "BUCKET must be one of: HOUR, DAY" >&2
      exit 1
      ;;
  esac
}

main() {
  require_command jq
  require_command date
  validate_rate
  validate_bucket
  trap cleanup_prepare_out_dir EXIT INT TERM

  if [[ ! -f "${DATASET_META_PATH}" ]]; then
    echo "Dataset metadata not found: ${DATASET_META_PATH}" >&2
    exit 1
  fi

  if [[ -e "${OUT_DIR}" ]]; then
    echo "OUT_DIR already exists: ${OUT_DIR}" >&2
    exit 1
  fi

  mkdir -p "${OUT_DIR}"
  chmod 700 "${OUT_DIR}"

  local from to expected_bucket_count
  from=$(jq -r '.rangeFrom' "${DATASET_META_PATH}")
  to=$(jq -r '.rangeTo' "${DATASET_META_PATH}")

  case "${BUCKET}" in
    HOUR) expected_bucket_count=168 ;;
    DAY) expected_bucket_count=7 ;;
  esac

  local meta_path="${OUT_DIR}/meta.json"
  jq -nc \
    --arg scenario "R3" \
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
    --arg queryPreset "${QUERY_PRESET}" \
    --arg rangeFrom "${from}" \
    --arg rangeTo "${to}" \
    --arg requestEndpoint "/api/v1/events/analytics/aggregates/time-buckets" \
    --arg from "${from}" \
    --arg to "${to}" \
    --arg externalUserId "${EXTERNAL_USER_ID}" \
    --arg eventType "${EVENT_TYPE_FILTER}" \
    --arg bucket "${BUCKET}" \
    --arg timezone "${TIMEZONE_NAME}" \
    --arg warmup "${WARMUP}" \
    --arg duration "${DURATION}" \
    --arg cooldown "${COOLDOWN}" \
    --argjson totalEvents "$(jq -r '.totalEvents' "${DATASET_META_PATH}")" \
    --argjson identifiedUsers "$(jq -r '.identifiedUsers' "${DATASET_META_PATH}")" \
    --argjson rawPathCount "$(jq -r '.rawPathCount' "${DATASET_META_PATH}")" \
    --argjson routeTemplateCount "$(jq -r '.routeTemplateCount' "${DATASET_META_PATH}")" \
    --argjson unmatchedPathCount "$(jq -r '.unmatchedPathCount' "${DATASET_META_PATH}")" \
    --argjson rawEventTypeCount "$(jq -r '.rawEventTypeCount' "${DATASET_META_PATH}")" \
    --argjson rate "${RATE}" \
    --argjson expectedBucketCount "${expected_bucket_count}" \
    --argjson preAllocatedVUs "$(case "${RATE}" in 10) echo 20 ;; 30) echo 60 ;; 50) echo 100 ;; esac)" \
    --argjson maxVUs "$(case "${RATE}" in 10) echo 60 ;; 30) echo 180 ;; 50) echo 300 ;; esac)" \
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
        datasetVersion: $datasetVersion,
        queryPreset: $queryPreset,
        totalEvents: $totalEvents,
        identifiedUsers: $identifiedUsers,
        rawPathCount: $rawPathCount,
        routeTemplateCount: $routeTemplateCount,
        unmatchedPathCount: $unmatchedPathCount,
        rawEventTypeCount: $rawEventTypeCount,
        rangeFrom: $rangeFrom,
        rangeTo: $rangeTo,
        expectedBucketCount: $expectedBucketCount
      },
      request: {
        endpoint: $requestEndpoint,
        from: $from,
        to: $to,
        bucket: $bucket,
        timezone: $timezone,
        externalUserId: (if $externalUserId == "" then null else $externalUserId end),
        eventType: (if $eventType == "" then null else $eventType end)
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
  echo "[r3-prepare] meta ready: ${meta_path}"
}

main "$@"
