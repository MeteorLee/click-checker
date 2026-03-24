#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PREPARE_BASE_URL="${PREPARE_BASE_URL:-http://localhost:8080}"
RUN_BASE_URL="${RUN_BASE_URL:-http://app:8080}"
ENV_NAME="${ENV_NAME:-local}"
EXECUTION_LOCATION="${EXECUTION_LOCATION:-local}"
DB_INSTANCE_LABEL="${DB_INSTANCE_LABEL:-local-postgres}"

exec env \
  PREPARE_BASE_URL="${PREPARE_BASE_URL}" \
  RUN_BASE_URL="${RUN_BASE_URL}" \
  ENV_NAME="${ENV_NAME}" \
  EXECUTION_LOCATION="${EXECUTION_LOCATION}" \
  DB_INSTANCE_LABEL="${DB_INSTANCE_LABEL}" \
  V2_ENV_HELPER_PATH="${SCRIPT_DIR}/v2-dataset-env.sh" \
  V2_ENSURE_FUNCTION="ensure_v2_dataset_local" \
  "${SCRIPT_DIR}/../../common/v2-generic-prepare.sh" "$@"
