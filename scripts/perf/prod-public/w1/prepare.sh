#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_DIRECT_PREPARE="${SCRIPT_DIR}/../../prod-direct/w1/prepare.sh"

RATE="${RATE:-50}"
RUN_ID="${RUN_ID:-w1-$(date +%Y%m%d-%H%M%S)-r${RATE}-prod-public}"
OUT_DIR="${OUT_DIR:-artifacts/perf/prod-public/w1/${RUN_ID}}"

exec env \
  RUN_ID="${RUN_ID}" \
  OUT_DIR="${OUT_DIR}" \
  ENV_NAME="${ENV_NAME:-prod-public}" \
  "${PROD_DIRECT_PREPARE}" "$@"
