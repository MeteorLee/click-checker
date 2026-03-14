#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
APP_ENV_FILE="${APP_ENV_FILE:-.env}"
DEPLOY_ENV_FILE="${DEPLOY_ENV_FILE:-.env.codedeploy}"
DISCORD_WEBHOOK_FILE="${ALERTMANAGER_DISCORD_WEBHOOK_FILE:-./.secrets/alertmanager/discord-webhook-url}"

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

main() {
  require_command docker
  docker compose version >/dev/null 2>&1 || {
    echo "docker compose is not available" >&2
    exit 1
  }

  [ -f "${APP_ENV_FILE}" ] || { echo "${APP_ENV_FILE} not found"; exit 1; }
  [ -f "${DEPLOY_ENV_FILE}" ] || { echo "${DEPLOY_ENV_FILE} not found"; exit 1; }
  [ -f "${DISCORD_WEBHOOK_FILE}" ] || { echo "${DISCORD_WEBHOOK_FILE} not found"; exit 1; }

  chmod 644 "${DISCORD_WEBHOOK_FILE}"

  echo "[observability] starting shared prod observability services"
  compose up -d prometheus loki promtail alertmanager grafana

  echo "[observability] service status"
  compose ps prometheus loki promtail alertmanager grafana
}

main "$@"
