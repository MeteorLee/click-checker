#!/usr/bin/env bash
set -euo pipefail

# Local-only blue/green switch helper for temporary verification.
# This script is intended for manual or local environment checks using
# nginx/click-checker-blue-green-template.conf and the temporary nginx container.
# It is not the main prod deployment entrypoint.
#
# Usage:
#   ./scripts/blue-green-local-switch.sh
#   ./scripts/blue-green-local-switch.sh blue
#   ./scripts/blue-green-local-switch.sh green
#
# Environment:
#   COMPOSE_FILES            Compose args override
#   NGINX_BG_CONFIG          Blue/Green nginx config path override
#   NGINX_BG_CONTAINER       Temporary nginx container name override
#   SKIP_RELOAD=1            Skip nginx container reload
#   SKIP_STOP_OLD=1          Keep old color running after switch
#
# Main prod entrypoint:
# - ./scripts/deploy-prod-orchestrator.sh
#

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
NGINX_BG_CONFIG="${NGINX_BG_CONFIG:-nginx/click-checker-blue-green-template.conf}"
NGINX_BG_CONTAINER="${NGINX_BG_CONTAINER:-click-checker-nginx-bg-test}"
NGINX_BG_IMAGE="${NGINX_BG_IMAGE:-nginx:1.25-alpine}"
NGINX_BG_NETWORK="${NGINX_BG_NETWORK:-click-checker_default}"
NGINX_BG_PORT="${NGINX_BG_PORT:-18080}"
PUBLIC_VERIFY_ATTEMPTS="${PUBLIC_VERIFY_ATTEMPTS:-5}"
PUBLIC_VERIFY_DELAY_SECONDS="${PUBLIC_VERIFY_DELAY_SECONDS:-2}"
TARGET_COLOR="${1:-}"

load_local_defaults() {
  if [[ -f .env ]]; then
    # shellcheck disable=SC1091
    set -a
    source .env
    set +a
  fi

  export DB_URL="${DB_URL:-jdbc:postgresql://postgres:5432/${POSTGRES_DB:-click_checker}}"
  export DB_USERNAME="${DB_USERNAME:-${POSTGRES_USER:-app}}"
  export DB_PASSWORD="${DB_PASSWORD:-${POSTGRES_PASSWORD:-apppw}}"
  export API_KEY_PEPPER="${API_KEY_PEPPER:-local-dev-pepper}"
  export API_KEY_ENV="${API_KEY_ENV:-live}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_command docker
require_command grep
require_command sed
require_command curl

compose() {
  # shellcheck disable=SC2086
  docker compose ${COMPOSE_FILES} "$@"
}

current_color() {
  if grep -q 'server app-blue:8081;' "${NGINX_BG_CONFIG}"; then
    echo "blue"
    return
  fi

  if grep -q 'server app-green:8082;' "${NGINX_BG_CONFIG}"; then
    echo "green"
    return
  fi

  echo "Could not detect active color from ${NGINX_BG_CONFIG}" >&2
  exit 1
}

other_color() {
  case "$1" in
    blue) echo "green" ;;
    green) echo "blue" ;;
    *)
      echo "Unknown color: $1" >&2
      exit 1
      ;;
  esac
}

target_port() {
  case "$1" in
    blue) echo "8081" ;;
    green) echo "8082" ;;
    *)
      echo "Unknown color: $1" >&2
      exit 1
      ;;
  esac
}

assert_color() {
  case "$1" in
    blue|green) ;;
    *)
      echo "Target color must be blue or green" >&2
      exit 1
      ;;
  esac
}

wait_for_readiness() {
  local color="$1"
  local port
  port=$(target_port "${color}")
  local attempt

  for attempt in $(seq 1 30); do
    if curl -fsS "http://127.0.0.1:${port}/actuator/health/readiness" | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 2
  done

  echo "Readiness check failed for ${color} on port ${port}" >&2
  exit 1
}

switch_upstream() {
  local color="$1"
  local from
  local to

  case "${color}" in
    blue)
      from='server app-green:8082;'
      to='server app-blue:8081;'
      ;;
    green)
      from='server app-blue:8081;'
      to='server app-green:8082;'
      ;;
    *)
      echo "Unknown color: ${color}" >&2
      exit 1
      ;;
  esac

  sed -i "s/${from}/${to}/" "${NGINX_BG_CONFIG}"
}

reload_bg_nginx() {
  if [[ "${SKIP_RELOAD:-0}" == "1" ]]; then
    echo "Skipping nginx reload"
    return
  fi

  if docker ps -a --format '{{.Names}}' | grep -qx "${NGINX_BG_CONTAINER}"; then
    docker rm -f "${NGINX_BG_CONTAINER}" >/dev/null
  fi

  docker run --rm -d \
    --name "${NGINX_BG_CONTAINER}" \
    --network "${NGINX_BG_NETWORK}" \
    -p "${NGINX_BG_PORT}:80" \
    -v "$(pwd)/${NGINX_BG_CONFIG}:/etc/nginx/conf.d/default.conf:ro" \
    "${NGINX_BG_IMAGE}" >/dev/null
}

verify_bg_nginx_color() {
  local color="$1"
  local attempt

  for attempt in $(seq 1 "${PUBLIC_VERIFY_ATTEMPTS}"); do
    if curl -fsS -H 'Host: clickchecker.dev' "http://127.0.0.1:${NGINX_BG_PORT}/" | grep -q "\"color\":\"${color}\""; then
      return 0
    fi
    sleep "${PUBLIC_VERIFY_DELAY_SECONDS}"
  done

  echo "Temporary nginx path did not report expected color: ${color}" >&2
  exit 1
}

stop_old_color() {
  local color="$1"

  if [[ "${SKIP_STOP_OLD:-0}" == "1" ]]; then
    echo "Skipping old color stop"
    return
  fi

  compose stop "app-${color}" >/dev/null
}

main() {
  load_local_defaults

  local active
  active=$(current_color)

  if [[ -z "${TARGET_COLOR}" ]]; then
    TARGET_COLOR=$(other_color "${active}")
  fi

  assert_color "${TARGET_COLOR}"

  if [[ "${TARGET_COLOR}" == "${active}" ]]; then
    echo "Target color is already active: ${TARGET_COLOR}" >&2
    exit 1
  fi

  local old_color
  old_color="${active}"

  echo "Active color : ${active}"
  echo "Target color : ${TARGET_COLOR}"

  echo "Starting app-${TARGET_COLOR}"
  compose up -d --build "app-${TARGET_COLOR}" >/dev/null

  echo "Waiting for readiness on ${TARGET_COLOR}"
  wait_for_readiness "${TARGET_COLOR}"

  echo "Switching upstream to ${TARGET_COLOR}"
  switch_upstream "${TARGET_COLOR}"

  echo "Reloading temporary nginx"
  reload_bg_nginx

  echo "Verifying temporary nginx response"
  verify_bg_nginx_color "${TARGET_COLOR}"

  echo "Stopping old color: ${old_color}"
  stop_old_color "${old_color}"

  echo "Switch complete"
}

main "$@"
