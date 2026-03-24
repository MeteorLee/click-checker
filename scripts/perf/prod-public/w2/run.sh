#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCENARIO_CODE="W2" "${SCRIPT_DIR}/../../common/v2-generic-run.sh" "$@"
