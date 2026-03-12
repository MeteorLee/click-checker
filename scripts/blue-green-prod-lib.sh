#!/usr/bin/env bash
set -euo pipefail

# Shared helper functions for prod blue/green deployment scripts.
# This file is intended to be sourced by orchestrator scripts and is not a
# standalone deployment entrypoint.
#
# Responsibilities:
# - detect current active color
# - switch nginx target between blue and green
# - wait for direct readiness on a specific color
#
# Main entrypoint:
# - ./scripts/deploy-prod-orchestrator.sh

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
NGINX_CONFIG="${NGINX_CONFIG:-/etc/nginx/sites-available/default}"
STATE_FILE="${STATE_FILE:-/var/run/click-checker-active-color}"
READINESS_ATTEMPTS="${READINESS_ATTEMPTS:-30}"
READINESS_DELAY_SECONDS="${READINESS_DELAY_SECONDS:-2}"

require_prod_lib_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Required command not found: $name" >&2
    exit 1
  fi
}

compose() {
  # shellcheck disable=SC2086
  docker compose ${COMPOSE_FILES} "$@"
}

assert_color() {
  case "$1" in
    blue|green) ;;
    *)
      echo "Color must be blue or green: $1" >&2
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
  if sudo test -f "${STATE_FILE}"; then
    local state_color
    state_color=$(sudo cat "${STATE_FILE}")
    case "${state_color}" in
      blue|green)
        echo "${state_color}"
        return 0
        ;;
    esac
  fi

  if sudo awk '
    /upstream click_checker_app[[:space:]]*\{/ { in_block=1; next }
    in_block && /\}/ { in_block=0 }
    in_block && /server 127\.0\.0\.1:8081;/ { found=1 }
    END { exit(found ? 0 : 1) }
  ' "${NGINX_CONFIG}"; then
    echo blue
    return 0
  fi

  if sudo awk '
    /upstream click_checker_app[[:space:]]*\{/ { in_block=1; next }
    in_block && /\}/ { in_block=0 }
    in_block && /server 127\.0\.0\.1:8082;/ { found=1 }
    END { exit(found ? 0 : 1) }
  ' "${NGINX_CONFIG}"; then
    echo green
    return 0
  fi

  if sudo grep -q 'proxy_pass http://127.0.0.1:8081;' "${NGINX_CONFIG}"; then
    echo blue
    return 0
  fi

  if sudo grep -q 'proxy_pass http://127.0.0.1:8082;' "${NGINX_CONFIG}"; then
    echo green
    return 0
  fi

  echo "Could not detect active color from nginx config" >&2
  exit 1
}

write_state_file() {
  local color="$1"
  sudo sh -c "printf '%s\n' '${color}' > '${STATE_FILE}'"
}

switch_upstream_target() {
  local port="$1"
  local tmp_file
  tmp_file=$(mktemp)

  sudo awk -v target_port="${port}" '
    /upstream click_checker_app[[:space:]]*\{/ { in_block=1 }
    in_block && /server 127\.0\.0\.1:(8081|8082);/ {
      sub(/127\.0\.0\.1:(8081|8082);/, "127.0.0.1:" target_port ";")
      in_block=0
    }
    { print }
  ' "${NGINX_CONFIG}" > "${tmp_file}"

  sudo cp "${tmp_file}" "${NGINX_CONFIG}"
  rm -f "${tmp_file}"
}

switch_proxy_pass_target() {
  local port="$1"
  sudo sed -i -E "s#proxy_pass http://127\\.0\\.0\\.1:(8081|8082);#proxy_pass http://127.0.0.1:${port};#g" "${NGINX_CONFIG}"
}

switch_nginx_target() {
  local color="$1"
  local port
  port=$(target_port "${color}")

  if sudo grep -q 'upstream click_checker_app' "${NGINX_CONFIG}"; then
    switch_upstream_target "${port}"
  else
    switch_proxy_pass_target "${port}"
  fi
}

reload_nginx() {
  sudo nginx -t >/dev/null
  sudo systemctl reload nginx
}

wait_for_readiness() {
  local color="$1"
  local port="$2"
  local attempt

  for attempt in $(seq 1 "${READINESS_ATTEMPTS}"); do
    if curl -fsS "http://127.0.0.1:${port}/actuator/health/readiness" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "[helper] readiness passed for ${color}"
      return 0
    fi
    echo "[helper] readiness retry ${attempt}/${READINESS_ATTEMPTS} for ${color}"
    sleep "${READINESS_DELAY_SECONDS}"
  done

  echo "[helper] readiness check failed for ${color} on port ${port}" >&2
  return 1
}

require_prod_lib_command docker
require_prod_lib_command curl
require_prod_lib_command grep
require_prod_lib_command sed
require_prod_lib_command sudo
