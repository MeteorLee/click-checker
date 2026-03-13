#!/usr/bin/env bash
set -euo pipefail

PROMETHEUS_URL="${PROMETHEUS_URL:-http://127.0.0.1:9090/-/ready}"
LOKI_URL="${LOKI_URL:-http://127.0.0.1:3100/ready}"
ALERTMANAGER_URL="${ALERTMANAGER_URL:-http://127.0.0.1:9093/-/ready}"
GRAFANA_URL="${GRAFANA_URL:-http://127.0.0.1:3000/api/health}"

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command not found: $name" >&2
    exit 1
  fi
}

check_ready() {
  local name="$1"
  local url="$2"

  echo "[observability:validate] checking ${name} -> ${url}"
  curl -fsS "${url}" >/dev/null
}

main() {
  require_command curl

  check_ready prometheus "${PROMETHEUS_URL}"
  check_ready loki "${LOKI_URL}"
  check_ready alertmanager "${ALERTMANAGER_URL}"
  check_ready grafana "${GRAFANA_URL}"

  echo "[observability:validate] all checks passed"
}

main "$@"
