#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
APP_ENV_FILE="${APP_ENV_FILE:-.env}"
DEPLOY_ENV_FILE="${DEPLOY_ENV_FILE:-.env.codedeploy}"

compose() {
  # shellcheck disable=SC2086
  docker compose --env-file "${APP_ENV_FILE}" --env-file "${DEPLOY_ENV_FILE}" ${COMPOSE_FILES} "$@"
}

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command not found: $name" >&2
    exit 1
  fi
}

check_http() {
  local name="$1"
  local service="$2"
  local url="$3"

  echo "[observability:validate] checking ${name}"
  compose exec -T "${service}" wget -qO- "${url}" >/dev/null
}

main() {
  require_command docker
  docker compose version >/dev/null 2>&1 || {
    echo "docker compose is not available" >&2
    exit 1
  }

  [ -f "${APP_ENV_FILE}" ] || { echo "${APP_ENV_FILE} not found"; exit 1; }
  [ -f "${DEPLOY_ENV_FILE}" ] || { echo "${DEPLOY_ENV_FILE} not found"; exit 1; }

  check_http prometheus prometheus "http://localhost:9090/-/ready"
  check_http loki loki "http://localhost:3100/ready"
  check_http alertmanager alertmanager "http://localhost:9093/-/ready"
  check_http renderer renderer "http://localhost:8081/metrics"
  check_http grafana grafana "http://localhost:3000/api/health"

  echo "[observability:validate] all checks passed"
}

main "$@"
