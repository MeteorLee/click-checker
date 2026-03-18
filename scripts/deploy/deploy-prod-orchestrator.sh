#!/usr/bin/env bash
set -euo pipefail

# Main prod deployment orchestrator for single-EC2 blue/green deployment.
#
# High-level flow:
# 1. detect active and target colors
# 2. start target color and wait for readiness
# 3. run direct smoke against target color
# 4. switch nginx to target color
# 5. verify public health and public smoke
# 6. trigger old color draining and poll drain status
# 7. stop old color
# 8. rollback nginx/containers on failure
#
# Shared low-level helpers are sourced from:
# - ./scripts/deploy/blue-green-prod-lib.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/blue-green-prod-lib.sh"

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
APP_DOMAIN="${APP_DOMAIN:-clickchecker.dev}"
NGINX_CONFIG="${NGINX_CONFIG:-/etc/nginx/sites-available/default}"
STATE_FILE="${STATE_FILE:-/var/run/click-checker-active-color}"
READINESS_ATTEMPTS="${READINESS_ATTEMPTS:-30}"
READINESS_DELAY_SECONDS="${READINESS_DELAY_SECONDS:-2}"
PUBLIC_VERIFY_ATTEMPTS="${PUBLIC_VERIFY_ATTEMPTS:-12}"
PUBLIC_VERIFY_DELAY_SECONDS="${PUBLIC_VERIFY_DELAY_SECONDS:-5}"
DRAIN_STATUS_ATTEMPTS="${DRAIN_STATUS_ATTEMPTS:-15}"
DRAIN_STATUS_DELAY_SECONDS="${DRAIN_STATUS_DELAY_SECONDS:-2}"
ACTIVE_COLOR=""
TARGET_COLOR=""
NGINX_SWITCHED=0
TARGET_STARTED=0

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command not found: $name" >&2
    exit 1
  fi
}

require_env() {
  local key="$1"
  if ! grep -Eq "^${key}=" .env; then
    echo "Missing required .env key: ${key}" >&2
    return 1
  fi
}

dump_logs() {
  echo "---- docker compose ps ----"
  compose ps || true
  echo "---- app-blue logs (tail=200) ----"
  compose logs --tail=200 app-blue || true
  echo "---- app-green logs (tail=200) ----"
  compose logs --tail=200 app-green || true
  echo "---- grafana logs (tail=100) ----"
  compose logs --tail=100 grafana || true
  echo "---- prometheus logs (tail=100) ----"
  compose logs --tail=100 prometheus || true
}

wait_public_health() {
  local attempt

  for attempt in $(seq 1 "${PUBLIC_VERIFY_ATTEMPTS}"); do
    if curl --max-time 5 --resolve "${APP_DOMAIN}:443:127.0.0.1" -fsS \
      "https://${APP_DOMAIN}/actuator/health" 2>/dev/null | jq -e '.status == "UP"' >/dev/null; then
      echo "[deploy] public health check passed"
      return 0
    fi
    echo "[deploy] public health retry ${attempt}/${PUBLIC_VERIFY_ATTEMPTS}"
    sleep "${PUBLIC_VERIFY_DELAY_SECONDS}"
  done

  return 1
}
run_smoke() {
  local base_url="$1"
  local public_resolve="${2:-0}"

  BASE_URL="${base_url}" \
  APP_DOMAIN="${APP_DOMAIN}" \
  PUBLIC_RESOLVE="${public_resolve}" \
  "${SCRIPT_DIR}/deploy-smoke.sh"
}

run_drain_and_stop() {
  local color="$1"
  local port="$2"

  APP_COLOR="${color}" \
  APP_PORT="${port}" \
  DRAIN_STATUS_ATTEMPTS="${DRAIN_STATUS_ATTEMPTS}" \
  DRAIN_STATUS_DELAY_SECONDS="${DRAIN_STATUS_DELAY_SECONDS}" \
  "${SCRIPT_DIR}/deploy-drain.sh"
}

rollback_on_error() {
  local exit_code=$?

  trap - ERR

  if [ "${exit_code}" -eq 0 ]; then
    return
  fi

  echo "[rollback] deploy failed" >&2

  if [ "${NGINX_SWITCHED}" -eq 1 ] && [ -n "${ACTIVE_COLOR}" ]; then
    echo "[rollback] restoring nginx to ${ACTIVE_COLOR}" >&2
    switch_nginx_target "${ACTIVE_COLOR}" || true
    if sudo nginx -t >/dev/null 2>&1; then
      sudo systemctl reload nginx >/dev/null 2>&1 || true
      write_state_file "${ACTIVE_COLOR}" || true
    fi
  fi

  if [ -n "${ACTIVE_COLOR}" ]; then
    echo "[rollback] ensuring app-${ACTIVE_COLOR} is running" >&2
    compose up -d "app-${ACTIVE_COLOR}" >/dev/null 2>&1 || true
  fi

  if [ -n "${TARGET_COLOR}" ] && [ "${TARGET_STARTED}" -eq 1 ]; then
    echo "[rollback] stopping app-${TARGET_COLOR}" >&2
    compose stop "app-${TARGET_COLOR}" >/dev/null 2>&1 || true
  fi

  dump_logs
  exit "${exit_code}"
}

main() {
  local target_port_value active_port direct_base_url public_base_url

  require_command jq
  require_command curl
  require_command docker
  require_command sudo
  [ -f .env ] || { echo ".env not found"; exit 1; }
  require_env DB_URL
  require_env DB_USERNAME
  require_env DB_PASSWORD
  require_env API_KEY_PEPPER
  require_env API_KEY_ENV
  require_env JWT_SECRET

  ACTIVE_COLOR=$(current_color)
  TARGET_COLOR=$(other_color "${ACTIVE_COLOR}")
  target_port_value=$(target_port "${TARGET_COLOR}")
  active_port=$(target_port "${ACTIVE_COLOR}")
  direct_base_url="http://127.0.0.1:${target_port_value}"
  public_base_url="https://${APP_DOMAIN}"

  echo "[deploy] active color=${ACTIVE_COLOR}"
  echo "[deploy] target color=${TARGET_COLOR}"

  echo "[deploy] starting target app-${TARGET_COLOR}"
  compose up -d "app-${TARGET_COLOR}" >/dev/null
  TARGET_STARTED=1

  wait_for_readiness "${TARGET_COLOR}" "${target_port_value}"

  echo "[deploy] running direct smoke on target color"
  run_smoke "${direct_base_url}" 0

  echo "[deploy] switching nginx to ${TARGET_COLOR}"
  switch_nginx_target "${TARGET_COLOR}"
  reload_nginx
  write_state_file "${TARGET_COLOR}"
  NGINX_SWITCHED=1

  wait_public_health

  echo "[deploy] running public smoke"
  run_smoke "${public_base_url}" 1

  run_drain_and_stop "${ACTIVE_COLOR}" "${active_port}"

  NGINX_SWITCHED=0
  echo "[deploy] completed successfully"
}

trap rollback_on_error ERR

main "$@"
