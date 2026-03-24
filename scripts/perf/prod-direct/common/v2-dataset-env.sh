#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../../common/v2-dataset-lib.sh"

V2_PROD_DATASET_VERSION_DEFAULT="${V2_PROD_DATASET_VERSION_DEFAULT:-${V2_DATASET_VERSION_DEFAULT}}"
V2_PROD_DATASET_DIR_DEFAULT="${V2_PROD_DATASET_DIR_DEFAULT:-artifacts/perf/prod-direct/common/datasets/${V2_PROD_DATASET_VERSION_DEFAULT}}"
V2_PROD_DATASET_META_PATH_DEFAULT="${V2_PROD_DATASET_META_PATH_DEFAULT:-${V2_PROD_DATASET_DIR_DEFAULT}/dataset.json}"
V2_PROD_ORG_NAME_PREFIX_DEFAULT="${V2_PROD_ORG_NAME_PREFIX_DEFAULT:-perf-v2-prod-direct}"

v2_require_prod_prepare_url() {
  if [[ -z "${PREPARE_BASE_URL:-}" ]]; then
    echo "PREPARE_BASE_URL is required" >&2
    exit 1
  fi
}

v2_prod_dataset_dir() {
  printf '%s\n' "${DATASET_DIR:-${V2_PROD_DATASET_DIR_DEFAULT}}"
}

v2_prod_dataset_meta_path() {
  printf '%s\n' "${DATASET_META_PATH:-${V2_PROD_DATASET_META_PATH_DEFAULT}}"
}

v2_prod_dataset_exists() {
  [[ -f "$(v2_prod_dataset_meta_path)" ]]
}

v2_dataset_meta_get() {
  local meta_path="$1"
  local jq_filter="$2"
  jq -r "${jq_filter}" "${meta_path}"
}

ensure_v2_dataset_prod_direct() {
  local dataset_meta_path
  local requested_reset_mode default_reset_mode effective_reset_mode
  dataset_meta_path="$(v2_prod_dataset_meta_path)"
  requested_reset_mode="${RESET_MODE:-}"
  default_reset_mode="$(v2_default_reset_mode_for "${SCENARIO_CODE:-R4}")"
  effective_reset_mode="${requested_reset_mode:-${default_reset_mode}}"

  v2_require_prod_prepare_url

  if v2_prod_dataset_exists; then
    if [[ -z "${requested_reset_mode}" ]]; then
      effective_reset_mode="$(v2_effective_reset_mode "${default_reset_mode}" "${dataset_meta_path}")"
    fi

    case "${effective_reset_mode}" in
      full|quick)
        if ! DATASET_META_PATH="${dataset_meta_path}" \
          RESET_MODE="${effective_reset_mode}" \
          "${SCRIPT_DIR}/v2-restore-snapshot.sh" >&2; then
          return 1
        fi
        ;;
      skip)
        echo "[v2] dataset present -> reset skipped" >&2
        ;;
      *)
        echo "Unsupported RESET_MODE: ${effective_reset_mode}" >&2
        exit 1
        ;;
    esac
  else
    if ! PREPARE_BASE_URL="${PREPARE_BASE_URL}" \
      DATASET_VERSION="${DATASET_VERSION:-${V2_PROD_DATASET_VERSION_DEFAULT}}" \
      DATASET_DIR="$(v2_prod_dataset_dir)" \
      DATASET_META_PATH="${dataset_meta_path}" \
      ORG_NAME_PREFIX="${ORG_NAME_PREFIX:-${V2_PROD_ORG_NAME_PREFIX_DEFAULT}}" \
      RESET_DATASET="${RESET_DATASET:-false}" \
      "${SCRIPT_DIR}/v2-seed-dataset.sh" >&2; then
      return 1
    fi
  fi

  if [[ -n "${V2_RESET_MODE_OUTPUT_PATH:-}" ]]; then
    printf '%s\n' "${effective_reset_mode}" > "${V2_RESET_MODE_OUTPUT_PATH}"
  fi
  printf '%s\n' "${dataset_meta_path}"
}
