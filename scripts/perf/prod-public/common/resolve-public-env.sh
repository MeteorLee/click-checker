#!/usr/bin/env bash
set -euo pipefail

APP_PUBLIC_BASE_URL_VALUE="${APP_PUBLIC_BASE_URL:-https://clickchecker.dev}"
K6_NETWORK_VALUE="${K6_NETWORK:-click-checker_default}"
GRAFANA_BASE_URL_VALUE="${GRAFANA_BASE_URL:-https://grafana.clickchecker.dev}"

main() {
  cat <<EOF
export APP_PUBLIC_BASE_URL=${APP_PUBLIC_BASE_URL_VALUE}
export PREPARE_BASE_URL=${APP_PUBLIC_BASE_URL_VALUE}
export RUN_BASE_URL=${APP_PUBLIC_BASE_URL_VALUE}
export K6_NETWORK=${K6_NETWORK_VALUE}
export GRAFANA_BASE_URL=${GRAFANA_BASE_URL_VALUE}
EOF
}

main "$@"
