#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

# shellcheck disable=SC1091
source "${REPO_ROOT}/scripts/deploy/blue-green-prod-lib.sh"

COLOR_OVERRIDE="${1:-${ACTIVE_COLOR_OVERRIDE:-}}"
K6_NETWORK_VALUE="${K6_NETWORK:-click-checker_default}"
GRAFANA_BASE_URL_VALUE="${GRAFANA_BASE_URL:-http://127.0.0.1:3000}"

resolve_color() {
  if [[ -n "${COLOR_OVERRIDE}" ]]; then
    assert_color "${COLOR_OVERRIDE}"
    printf '%s\n' "${COLOR_OVERRIDE}"
    return
  fi

  current_color
}

main() {
  local color port app_service
  color="$(resolve_color)"
  port="$(target_port "${color}")"
  app_service="app-${color}"

  cat <<EOF
export ACTIVE_COLOR=${color}
export ACTIVE_PORT=${port}
export PREPARE_BASE_URL=http://127.0.0.1:${port}
export RUN_BASE_URL=http://${app_service}:${port}
export K6_NETWORK=${K6_NETWORK_VALUE}
export GRAFANA_BASE_URL=${GRAFANA_BASE_URL_VALUE}
EOF
}

main "$@"
