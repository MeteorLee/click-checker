#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec env SCENARIO_CODE="R6" "${SCRIPT_DIR}/../common/v2-prepare-scenario.sh" "$@"
