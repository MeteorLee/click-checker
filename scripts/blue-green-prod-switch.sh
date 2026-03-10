#!/usr/bin/env bash
set -euo pipefail

# Switch the production app between app-blue(8081) and app-green(8082).
#
# Usage:
#   ./scripts/blue-green-prod-switch.sh
#   ./scripts/blue-green-prod-switch.sh blue
#   ./scripts/blue-green-prod-switch.sh green
#
# Requirements:
# - Run on the EC2 host
# - /etc/nginx/sites-available/default is the active nginx config
# - app-blue/app-green are defined in docker-compose.prod.yml
#
# Environment:
#   COMPOSE_FILES            Compose args override
#   NGINX_CONFIG             Active nginx config path
#   SKIP_STOP_OLD=1          Keep old color running after switch
#   SKIP_BUILD=1             Skip --build on target color startup
#   STABILIZE_SECONDS        Wait time after direct app verification

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
NGINX_CONFIG="${NGINX_CONFIG:-/etc/nginx/sites-available/default}"
STABILIZE_SECONDS="${STABILIZE_SECONDS:-5}"
TARGET_COLOR="${1:-}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_command docker
require_command curl
require_command grep
require_command sed
require_command sudo

compose() {
  # shellcheck disable=SC2086
  docker compose ${COMPOSE_FILES} "$@"
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

current_color() {
  if sudo grep -q 'server 127.0.0.1:8081;' "${NGINX_CONFIG}"; then
    echo "blue"
    return
  fi

  if sudo grep -q 'server 127.0.0.1:8082;' "${NGINX_CONFIG}"; then
    echo "green"
    return
  fi

  if sudo grep -q 'proxy_pass http://127.0.0.1:8081;' "${NGINX_CONFIG}"; then
    echo "blue"
    return
  fi

  if sudo grep -q 'proxy_pass http://127.0.0.1:8082;' "${NGINX_CONFIG}"; then
    echo "green"
    return
  fi

  echo "Could not detect active color from ${NGINX_CONFIG}" >&2
  exit 1
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

verify_root_color() {
  local color="$1"
  local port
  port=$(target_port "${color}")

  if ! curl -fsS "http://127.0.0.1:${port}/" | grep -q "\"color\":\"${color}\""; then
    echo "Root response did not report expected color: ${color}" >&2
    exit 1
  fi
}

switch_nginx_target() {
  local color="$1"
  local port
  port=$(target_port "${color}")

  if sudo grep -q 'upstream click_checker_app' "${NGINX_CONFIG}"; then
    sudo sed -i -E "0,/server 127\\.0\\.0\\.1:(8081|8082);/s//server 127.0.0.1:${port};/" "${NGINX_CONFIG}"
  else
    sudo sed -i -E "s#proxy_pass http://127\\.0\\.0\\.1:(8081|8082);#proxy_pass http://127.0.0.1:${port};#g" "${NGINX_CONFIG}"
  fi
}

verify_nginx_color() {
  local color="$1"
  if ! curl -fsS --resolve clickchecker.dev:443:127.0.0.1 https://clickchecker.dev | grep -q "\"color\":\"${color}\""; then
    echo "Public nginx path did not report expected color: ${color}" >&2
    exit 1
  fi
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

  if [[ "${SKIP_BUILD:-0}" == "1" ]]; then
    echo "Starting app-${TARGET_COLOR} without build"
    compose up -d "app-${TARGET_COLOR}" >/dev/null
  else
    echo "Starting app-${TARGET_COLOR} with build"
    compose up -d --build "app-${TARGET_COLOR}" >/dev/null
  fi

  echo "Waiting for readiness on ${TARGET_COLOR}"
  wait_for_readiness "${TARGET_COLOR}"

  echo "Verifying direct app response"
  verify_root_color "${TARGET_COLOR}"

  echo "Waiting ${STABILIZE_SECONDS}s for app stabilization"
  sleep "${STABILIZE_SECONDS}"

  echo "Switching nginx target to ${TARGET_COLOR}"
  switch_nginx_target "${TARGET_COLOR}"

  echo "Validating nginx config"
  sudo nginx -t >/dev/null

  echo "Reloading nginx"
  sudo systemctl reload nginx

  echo "Verifying public response"
  verify_nginx_color "${TARGET_COLOR}"

  echo "Stopping old color: ${old_color}"
  stop_old_color "${old_color}"

  echo "Switch complete"
}

main "$@"
