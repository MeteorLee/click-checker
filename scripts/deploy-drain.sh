#!/usr/bin/env bash
set -euo pipefail

# Drain and stop an old color after nginx switch.
#
# Required env:
# - APP_COLOR
# - APP_PORT
#
# Optional env:
# - DRAIN_STATUS_ATTEMPTS
# - DRAIN_STATUS_DELAY_SECONDS

APP_COLOR="${APP_COLOR:-}"
APP_PORT="${APP_PORT:-}"
DRAIN_STATUS_ATTEMPTS="${DRAIN_STATUS_ATTEMPTS:-15}"
DRAIN_STATUS_DELAY_SECONDS="${DRAIN_STATUS_DELAY_SECONDS:-2}"

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command not found: $name" >&2
    exit 1
  fi
}

require_env_value() {
  local key="$1"
  local value="$2"
  if [ -z "${value}" ]; then
    echo "Missing required env: ${key}" >&2
    exit 1
  fi
}

start_draining() {
  echo "[drain] start draining ${APP_COLOR}"
  curl -fsS -X POST "http://127.0.0.1:${APP_PORT}/internal/drain/start" >/tmp/drain_start.json
  cat /tmp/drain_start.json
}

wait_for_drain_to_settle() {
  local attempt status_json state active_requests

  state="unknown"
  active_requests="unknown"

  for attempt in $(seq 1 "${DRAIN_STATUS_ATTEMPTS}"); do
    if ! status_json=$(curl -fsS "http://127.0.0.1:${APP_PORT}/internal/drain/status" 2>/dev/null); then
      echo "[drain] status retry ${attempt}/${DRAIN_STATUS_ATTEMPTS} color=${APP_COLOR} reason=status_endpoint_unavailable"
      sleep "${DRAIN_STATUS_DELAY_SECONDS}"
      continue
    fi

    state=$(printf '%s' "${status_json}" | jq -r '.trafficState')
    active_requests=$(printf '%s' "${status_json}" | jq -r '.activeRequests')

    echo "[drain] status ${attempt}/${DRAIN_STATUS_ATTEMPTS} color=${APP_COLOR} state=${state} activeRequests=${active_requests}"

    if [ "${state}" = "DRAINING" ] && [ "${active_requests}" = "0" ]; then
      echo "[drain] settled for ${APP_COLOR}"
      return 0
    fi

    sleep "${DRAIN_STATUS_DELAY_SECONDS}"
  done

  echo "[drain] did not fully settle for ${APP_COLOR} before timeout, continuing with graceful stop state=${state} activeRequests=${active_requests}"
  return 0
}

stop_color() {
  echo "[drain] stopping app-${APP_COLOR}"
  docker compose \
    --env-file .env \
    --env-file .env.codedeploy \
    -f docker-compose.yml \
    -f docker-compose.prod.yml \
    stop "app-${APP_COLOR}" >/dev/null
}

main() {
  require_command curl
  require_command jq
  require_command docker
  require_env_value APP_COLOR "${APP_COLOR}"
  require_env_value APP_PORT "${APP_PORT}"

  [ -f .env ] || { echo ".env not found"; exit 1; }
  [ -f .env.codedeploy ] || { echo ".env.codedeploy not found"; exit 1; }

  start_draining
  wait_for_drain_to_settle
  stop_color
}

main "$@"
