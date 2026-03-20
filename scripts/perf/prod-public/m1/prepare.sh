#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_DIRECT_PREPARE="${SCRIPT_DIR}/../../prod-direct/m1/prepare.sh"

PAIR_RATE="${PAIR_RATE:-10}"
WRITE_RATE="${WRITE_RATE:-${PAIR_RATE}}"
READ_RATE="${READ_RATE:-${PAIR_RATE}}"
RUN_ID="${RUN_ID:-m1-$(date +%Y%m%d-%H%M%S)-w${WRITE_RATE}-r${READ_RATE}-prod-public}"
OUT_DIR="${OUT_DIR:-artifacts/perf/prod-public/m1/${RUN_ID}}"
DATASET_VERSION="${DATASET_VERSION:-m1-v1}"
SNAPSHOT_VERSION="${SNAPSHOT_VERSION:-${DATASET_VERSION}-snap1}"
DATASET_DIR="${DATASET_DIR:-artifacts/perf/prod-public/m1/datasets/${DATASET_VERSION}}"
DATASET_META_PATH="${DATASET_META_PATH:-${DATASET_DIR}/dataset.json}"
DATASET_ORG_NAME="${DATASET_ORG_NAME:-perf-m1-prod-public-dataset}"

exec env \
  RUN_ID="${RUN_ID}" \
  OUT_DIR="${OUT_DIR}" \
  DATASET_VERSION="${DATASET_VERSION}" \
  SNAPSHOT_VERSION="${SNAPSHOT_VERSION}" \
  DATASET_DIR="${DATASET_DIR}" \
  DATASET_META_PATH="${DATASET_META_PATH}" \
  DATASET_ORG_NAME="${DATASET_ORG_NAME}" \
  ENV_NAME="${ENV_NAME:-prod-public}" \
  "${PROD_DIRECT_PREPARE}" "$@"
