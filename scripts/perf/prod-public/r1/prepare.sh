#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_DIRECT_PREPARE="${SCRIPT_DIR}/../../prod-direct/r1/prepare.sh"

RATE="${RATE:-10}"
RUN_ID="${RUN_ID:-r1-$(date +%Y%m%d-%H%M%S)-r${RATE}-prod-public}"
OUT_DIR="${OUT_DIR:-artifacts/perf/prod-public/r1/${RUN_ID}}"
DATASET_VERSION="${DATASET_VERSION:-r1-v1}"
DATASET_DIR="${DATASET_DIR:-artifacts/perf/prod-public/r1/datasets/${DATASET_VERSION}}"
DATASET_META_PATH="${DATASET_META_PATH:-${DATASET_DIR}/dataset.json}"
DATASET_ORG_NAME="${DATASET_ORG_NAME:-perf-r1-prod-public-dataset}"

exec env \
  RUN_ID="${RUN_ID}" \
  OUT_DIR="${OUT_DIR}" \
  DATASET_VERSION="${DATASET_VERSION}" \
  DATASET_DIR="${DATASET_DIR}" \
  DATASET_META_PATH="${DATASET_META_PATH}" \
  DATASET_ORG_NAME="${DATASET_ORG_NAME}" \
  ENV_NAME="${ENV_NAME:-prod-public}" \
  "${PROD_DIRECT_PREPARE}" "$@"
