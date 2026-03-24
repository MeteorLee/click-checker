#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/v2-scenario-lib.sh"

SCENARIO_CODE="${SCENARIO_CODE:-}"
V2_ENV_HELPER_PATH="${V2_ENV_HELPER_PATH:-}"
V2_ENSURE_FUNCTION="${V2_ENSURE_FUNCTION:-}"

PREPARE_BASE_URL="${PREPARE_BASE_URL:-}"
RUN_BASE_URL="${RUN_BASE_URL:-}"
ENV_NAME="${ENV_NAME:-local}"
EXECUTION_LOCATION="${EXECUTION_LOCATION:-local}"
DB_INSTANCE_LABEL="${DB_INSTANCE_LABEL:-local-postgres}"
GRAFANA_BASE_URL_META="${GRAFANA_BASE_URL_META:-${GRAFANA_BASE_URL:-}}"
STAGE="${STAGE:-1}"
PROFILE_VERSION="${PROFILE_VERSION:-}"
READ_PROFILE_VERSION="${READ_PROFILE_VERSION:-}"
RESET_MODE="${RESET_MODE:-}"
PRESET="${PRESET:-}"
CAPTURE_MODE="${CAPTURE_MODE:-manual}"
CAPTURE_PROFILE="${CAPTURE_PROFILE:-}"
CAPTURE_TIME_RANGE="${CAPTURE_TIME_RANGE:-}"
IS_BASELINE="${IS_BASELINE:-false}"
COMPARE_TO="${COMPARE_TO:-}"
APP_COUNT="${APP_COUNT:-1}"
PREPARE_COMPLETED="false"

cleanup_prepare_out_dir() {
  local exit_code=$?

  if [[ "${PREPARE_COMPLETED}" != "true" && -n "${OUT_DIR:-}" && -d "${OUT_DIR}" ]]; then
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

validate_prepare_inputs() {
  if [[ -z "${SCENARIO_CODE}" ]]; then
    echo "SCENARIO_CODE is required" >&2
    exit 1
  fi

  if [[ -z "${V2_ENV_HELPER_PATH}" || ! -f "${V2_ENV_HELPER_PATH}" ]]; then
    echo "V2_ENV_HELPER_PATH is required" >&2
    exit 1
  fi

  if [[ -z "${V2_ENSURE_FUNCTION}" ]]; then
    echo "V2_ENSURE_FUNCTION is required" >&2
    exit 1
  fi

  if [[ -z "${RUN_BASE_URL}" ]]; then
    echo "RUN_BASE_URL is required" >&2
    exit 1
  fi

  v2_require_stage "${STAGE}"

  if [[ -n "${RESET_MODE}" ]]; then
    v2_require_reset_mode "${RESET_MODE}"
  fi

  case "${SCENARIO_CODE}" in
    R4|R5|R6)
      if [[ -n "${PROFILE_VERSION}" ]]; then
        v2_require_profile_version "${PROFILE_VERSION}"
      fi
      ;;
    M2)
      if [[ -n "${READ_PROFILE_VERSION}" ]]; then
        v2_require_profile_version "${READ_PROFILE_VERSION}"
      fi
      ;;
  esac

  case "${SCENARIO_CODE}" in
    W2|R4|R5|R6|M2) ;;
    *)
      echo "Unsupported SCENARIO_CODE: ${SCENARIO_CODE}" >&2
      exit 1
      ;;
  esac
}

default_run_id() {
  local scenario_key="$1"
  local stage_label="$2"
  local profile_label="$3"
  local preset="$4"
  local rate_json="$5"
  local read_profile_label="${6:-}"
  local now

  now="$(date +%Y%m%d-%H%M%S)"

  case "${SCENARIO_CODE}" in
    W2)
      printf '%s-%s-%s-r%s\n' "${scenario_key}" "${now}" "${stage_label}" "$(jq -r '.rate' <<<"${rate_json}")"
      ;;
    R4|R5|R6)
      printf '%s-%s-%s-%s-%s-r%s\n' "${scenario_key}" "${now}" "${stage_label}" "${profile_label}" "${preset}" "$(jq -r '.rate' <<<"${rate_json}")"
      ;;
    M2)
      printf '%s-%s-%s-%s-%s-w%s-r%s\n' \
        "${scenario_key}" \
        "${now}" \
        "${stage_label}" \
        "${read_profile_label}" \
        "${preset}" \
        "$(jq -r '.writeRate' <<<"${rate_json}")" \
        "$(jq -r '.readRate' <<<"${rate_json}")"
      ;;
  esac
}

default_state_mutation() {
  case "${SCENARIO_CODE}" in
    W2|M2) printf 'write_overlay\n' ;;
    R4|R5|R6) printf 'read_only\n' ;;
    *)
      echo "Unsupported scenario for state mutation: ${SCENARIO_CODE}" >&2
      exit 1
      ;;
  esac
}

build_dataset_json() {
  local dataset_meta_path="$1"
  jq -c \
    --arg metaPath "${dataset_meta_path}" \
    '{
      metaPath: $metaPath,
      datasetVersion,
      snapshotVersion,
      createdAt,
      seedAnchorAt,
      schemaFingerprint,
      appCommit,
      totals,
      distributions,
      funnelRules,
      retentionRules,
      routeTemplateCount,
      eventTypeMappingCount,
      orgs: [.orgs[] | {
        key,
        tier,
        orgId,
        orgName,
        sharePct,
        totalEvents,
        identifiedUsers
      }]
    }' "${dataset_meta_path}"
}

build_auth_orgs_json() {
  local dataset_meta_path="$1"
  jq -c '[.orgs[] | {key, tier, orgId, orgName, apiKey, apiKeyPrefix, sharePct}]' "${dataset_meta_path}"
}

build_request_json() {
  local dataset_meta_path="$1"
  local preset="$2"
  local profile_version="$3"
  local profile_label="$4"
  local seed_anchor_at="$5"
  local read_profile_version="${6:-}"
  local read_profile_label="${7:-}"
  local request_json write_json read_json

  case "${SCENARIO_CODE}" in
    W2)
      write_json="$(v2_w2_request_json "${dataset_meta_path}")"
      jq -nc \
        --arg preset "${preset}" \
        --arg profileLabel "" \
        --argjson write "${write_json}" \
        '{preset:$preset,profileLabel:$profileLabel,write:$write}'
      ;;
    R4)
      jq -nc \
        --arg preset "${preset}" \
        --arg profileLabel "${profile_label}" \
        --argjson profileVersion "${profile_version}" \
        --argjson profiles "$(v2_r4_profiles_json "${seed_anchor_at}" "${preset}" "${profile_version}")" \
        '{preset:$preset,profileVersion:$profileVersion,profileLabel:$profileLabel,profiles:$profiles}'
      ;;
    R5)
      jq -nc \
        --arg preset "${preset}" \
        --arg profileLabel "${profile_label}" \
        --argjson profileVersion "${profile_version}" \
        --argjson profiles "$(v2_r5_profiles_json "${seed_anchor_at}" "${preset}" "${profile_version}")" \
        '{preset:$preset,profileVersion:$profileVersion,profileLabel:$profileLabel,profiles:$profiles}'
      ;;
    R6)
      v2_r6_request_json "${seed_anchor_at}" "${preset}" "${profile_version}"
      ;;
    M2)
      write_json="$(v2_w2_request_json "${dataset_meta_path}")"
      read_json="$(v2_r6_request_json "${seed_anchor_at}" "${preset}" "${read_profile_version}")"
      jq -nc \
        --arg preset "${preset}" \
        --arg readProfileLabel "${read_profile_label}" \
        --argjson readProfileVersion "${read_profile_version}" \
        --argjson write "${write_json}" \
        --argjson read "${read_json}" \
        '{preset:$preset,readProfileVersion:$readProfileVersion,readProfileLabel:$readProfileLabel,write:$write,read:$read}'
      ;;
  esac
}

build_load_json() {
  local rate_json="$1"
  local duration_json="$2"
  local vus_json="$3"
  local thresholds_json="$4"
  jq -nc \
    --argjson rate "${rate_json}" \
    --argjson duration "${duration_json}" \
    --argjson vus "${vus_json}" \
    --argjson thresholds "${thresholds_json}" \
    '$rate + $duration + $vus + $thresholds'
}

main() {
  local scenario_key stage_label profile_version profile_label read_profile_version read_profile_label
  local preset requested_reset_mode reset_mode state_mutation rate_json duration_json thresholds_json vus_json
  local dataset_meta_path dataset_json auth_orgs_json request_json load_json
  local run_id out_dir meta_path seed_anchor_at reset_mode_output_path

  require_command jq
  require_command date
  validate_prepare_inputs
  # shellcheck disable=SC1090
  source "${V2_ENV_HELPER_PATH}"

  stage_label="$(v2_stage_label "${STAGE}")"
  profile_version=""
  profile_label=""
  read_profile_version=""
  read_profile_label=""

  case "${SCENARIO_CODE}" in
    R4|R5|R6)
      profile_version="${PROFILE_VERSION:-$(v2_default_profile_version_for "${SCENARIO_CODE}" "${STAGE}")}"
      profile_label="$(v2_profile_label "${profile_version}")"
      preset="${PRESET:-$(v2_default_preset_for "${SCENARIO_CODE}" "${profile_version}")}"
      ;;
    M2)
      read_profile_version="${READ_PROFILE_VERSION:-$(v2_default_profile_version_for "${SCENARIO_CODE}" "${STAGE}")}"
      read_profile_label="$(v2_profile_label "${read_profile_version}")"
      preset="${PRESET:-$(v2_default_preset_for "${SCENARIO_CODE}" "${STAGE}")}"
      ;;
    *)
      preset="${PRESET:-$(v2_default_preset_for "${SCENARIO_CODE}" "${STAGE}")}"
      ;;
  esac
  requested_reset_mode="${RESET_MODE:-}"
  reset_mode="${requested_reset_mode:-$(v2_default_reset_mode_for "${SCENARIO_CODE}")}"
  scenario_key="$(v2_scenario_key "${SCENARIO_CODE}")"
  rate_json="$(v2_rate_json_for "${SCENARIO_CODE}" "${STAGE}")"
  duration_json="$(v2_duration_json_for "${ENV_NAME}" "${STAGE}")"
  thresholds_json="$(v2_thresholds_json_for "${SCENARIO_CODE}")"
  vus_json="$(v2_vus_json_for "${SCENARIO_CODE}" "${STAGE}")"

  run_id="${RUN_ID:-$(default_run_id "${scenario_key}" "${stage_label}" "${profile_label}" "${preset}" "${rate_json}" "${read_profile_label}")}"
  out_dir="${OUT_DIR:-artifacts/perf/${ENV_NAME}/${scenario_key}/${run_id}}"

  trap cleanup_prepare_out_dir EXIT INT TERM

  if [[ -e "${out_dir}" ]]; then
    echo "OUT_DIR already exists: ${out_dir}" >&2
    exit 1
  fi

  mkdir -p "${out_dir}"
  chmod 700 "${out_dir}"

  reset_mode_output_path=$(mktemp)
  dataset_meta_path="$(RESET_MODE="${requested_reset_mode}" V2_RESET_MODE_OUTPUT_PATH="${reset_mode_output_path}" "${V2_ENSURE_FUNCTION}")"
  if [[ -s "${reset_mode_output_path}" ]]; then
    reset_mode="$(tr -d '\n' < "${reset_mode_output_path}")"
  fi
  rm -f "${reset_mode_output_path}"
  state_mutation="$(default_state_mutation)"
  seed_anchor_at="$(jq -r '.seedAnchorAt' "${dataset_meta_path}")"
  dataset_json="$(build_dataset_json "${dataset_meta_path}")"
  auth_orgs_json="$(build_auth_orgs_json "${dataset_meta_path}")"
  request_json="$(build_request_json "${dataset_meta_path}" "${preset}" "${profile_version}" "${profile_label}" "${seed_anchor_at}" "${read_profile_version}" "${read_profile_label}")"
  load_json="$(build_load_json "${rate_json}" "${duration_json}" "${vus_json}" "${thresholds_json}")"
  meta_path="${out_dir}/meta.json"

  jq -nc \
    --arg scenario "${SCENARIO_CODE}" \
    --arg scenarioKey "${scenario_key}" \
    --arg runId "${run_id}" \
    --arg timestamp "$(date --iso-8601=seconds)" \
    --arg status "prepared" \
    --arg stage "${STAGE}" \
    --arg stageLabel "${stage_label}" \
    --arg profileVersion "${profile_version}" \
    --arg profileLabel "${profile_label}" \
    --arg readProfileVersion "${read_profile_version}" \
    --arg readProfileLabel "${read_profile_label}" \
    --arg preset "${preset}" \
    --argjson isBaseline "$( [[ "${IS_BASELINE}" == "true" ]] && printf true || printf false )" \
    --arg compareTo "${COMPARE_TO}" \
    --arg resetMode "${reset_mode}" \
    --arg stateMutation "${state_mutation}" \
    --arg envName "${ENV_NAME}" \
    --arg prepareBaseUrl "${PREPARE_BASE_URL}" \
    --arg baseUrl "${RUN_BASE_URL}" \
    --argjson appCount "${APP_COUNT}" \
    --arg dbInstance "${DB_INSTANCE_LABEL}" \
    --arg executionLocation "${EXECUTION_LOCATION}" \
    --arg grafanaBaseUrl "${GRAFANA_BASE_URL_META}" \
    --arg captureMode "${CAPTURE_MODE}" \
    --arg captureProfile "${CAPTURE_PROFILE:-${scenario_key}}" \
    --arg captureTimeRange "${CAPTURE_TIME_RANGE}" \
    --arg outDir "${out_dir}" \
    --argjson authOrgs "${auth_orgs_json}" \
    --argjson dataset "${dataset_json}" \
    --argjson request "${request_json}" \
    --argjson load "${load_json}" \
    '{
      scenario: $scenario,
      scenarioKey: $scenarioKey,
      runId: $runId,
      timestamp: $timestamp,
      status: $status,
      stage: ($stage | tonumber),
      stageLabel: $stageLabel,
      profileVersion: (if $profileVersion == "" then null else ($profileVersion | tonumber) end),
      profileLabel: (if $profileLabel == "" then null else $profileLabel end),
      readProfileVersion: (if $readProfileVersion == "" then null else ($readProfileVersion | tonumber) end),
      readProfileLabel: (if $readProfileLabel == "" then null else $readProfileLabel end),
      preset: $preset,
      isBaseline: $isBaseline,
      compareTo: (if $compareTo == "" then null else $compareTo end),
      resetMode: $resetMode,
      stateMutation: $stateMutation,
      env: {
        name: $envName,
        prepareBaseUrl: (if $prepareBaseUrl == "" then null else $prepareBaseUrl end),
        baseUrl: $baseUrl,
        appCount: $appCount,
        dbInstance: $dbInstance,
        executionLocation: $executionLocation,
        grafanaBaseUrl: (if $grafanaBaseUrl == "" then null else $grafanaBaseUrl end)
      },
      auth: {
        orgs: $authOrgs
      },
      dataset: $dataset,
      request: $request,
      load: $load,
      capture: {
        mode: $captureMode,
        profile: $captureProfile,
        timeRange: (if $captureTimeRange == "" then null else $captureTimeRange end)
      },
      artifacts: {
        outDir: $outDir
      }
    }' > "${meta_path}"

  chmod 600 "${meta_path}"
  PREPARE_COMPLETED="true"
  echo "[${scenario_key}-prepare] meta ready: ${meta_path}"
}

main "$@"
