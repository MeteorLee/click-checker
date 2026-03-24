#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_NAME="${ENV_NAME:-prod-direct}"
EXECUTION_LOCATION="${EXECUTION_LOCATION:-ec2-prod}"
DB_INSTANCE_LABEL="${DB_INSTANCE_LABEL:-prod-rds}"

exec env \
  ENV_NAME="${ENV_NAME}" \
  EXECUTION_LOCATION="${EXECUTION_LOCATION}" \
  DB_INSTANCE_LABEL="${DB_INSTANCE_LABEL}" \
  V2_ENV_HELPER_PATH="${SCRIPT_DIR}/v2-dataset-env.sh" \
  V2_ENSURE_FUNCTION="ensure_v2_dataset_prod_direct" \
  "${SCRIPT_DIR}/../../common/v2-generic-prepare.sh" "$@"
