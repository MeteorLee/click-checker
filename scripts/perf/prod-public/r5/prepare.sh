#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec env ENV_NAME="prod-public" SCENARIO_CODE="R5" "${SCRIPT_DIR}/../../prod-direct/common/v2-prepare-scenario.sh" "$@"
